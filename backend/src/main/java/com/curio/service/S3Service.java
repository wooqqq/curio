package com.curio.service;

import com.curio.common.UrlGuard;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    /** 이미지 다운로드 상한 — 무제한이면 초대용량 URL로 메모리 폭증(DoS) 위험. */
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.region:ap-northeast-2}")
    private String region;

    public String uploadFromUrl(String imageUrl, Long userId) {
        if (!StringUtils.hasText(bucket)) {
            log.warn("S3 bucket not configured, skipping upload");
            return null;
        }
        try {
            // SSRF 방지: 내부/사설 대역으로 향하면 BLOCKED_URL → 아래 catch에서 null(다운로드 안 함).
            UrlGuard.verifyPublic(imageUrl);
            byte[] data = downloadBytes(imageUrl);
            String contentType = detectContentType(imageUrl);
            String ext = contentType.contains("png") ? "png" : "jpg";
            String key = buildKey(userId, ext);
            upload(data, contentType, key);
            return key;
        } catch (Exception e) {
            log.error("S3 upload failed for {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 관리자가 올린 이미지 파일을 S3에 업로드하고 공개 URL을 반환한다(팝업 배너용).
     * Content-Type 헤더는 위조 가능하므로 신뢰하지 않고, 파일 시그니처(magic byte)로
     * jpeg/png/webp/gif만 허용한다(svg 등 스크립트 포함 가능 타입 차단).
     * uploadFromUrl과 달리 실패를 삼키지 않고 예외로 알린다 — 관리자가 결과를 즉시 알아야 하므로.
     */
    public String uploadImage(byte[] data, Long userId) {
        if (!StringUtils.hasText(bucket)) {
            throw new CurioException(ErrorCode.S3_NOT_CONFIGURED);
        }
        ImageType type = detectImageType(data); // 허용되지 않으면 INVALID_IMAGE
        String key = buildKey(userId, type.ext);
        try {
            upload(data, type.contentType, key);
        } catch (Exception e) {
            log.error("S3 image upload failed: {}", e.getMessage());
            throw new CurioException(ErrorCode.UPLOAD_FAILED);
        }
        return getPublicUrl(key);
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp");

        final String contentType;
        final String ext;

        ImageType(String contentType, String ext) {
            this.contentType = contentType;
            this.ext = ext;
        }
    }

    /** 파일 앞부분 시그니처로 실제 이미지 타입을 판별. allowlist에 없으면 INVALID_IMAGE. */
    private ImageType detectImageType(byte[] d) {
        if (d == null || d.length < 12) {
            throw new CurioException(ErrorCode.INVALID_IMAGE);
        }
        // JPEG: FF D8 FF
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return ImageType.PNG;
        }
        // GIF: "GIF87a" / "GIF89a"
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return ImageType.GIF;
        }
        // WEBP: "RIFF"...."WEBP"
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return ImageType.WEBP;
        }
        throw new CurioException(ErrorCode.INVALID_IMAGE);
    }

    public String getPublicUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private void upload(byte[] data, String contentType, String key) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data)
        );
    }

    private byte[] downloadBytes(String url) throws Exception {
        // 리다이렉트는 따라가지 않는다 — 302로 내부 IP를 가리키는 SSRF 우회 차단(검증은 verifyPublic이 초기 URL에 대해 수행).
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            // 상한+1만큼 읽어 초과 여부를 판별한다(전체를 메모리에 올리지 않도록 캡).
            byte[] data = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);
            if (data.length > MAX_DOWNLOAD_BYTES) {
                throw new IOException("download exceeds " + MAX_DOWNLOAD_BYTES + " bytes");
            }
            return data;
        }
    }

    private String buildKey(Long userId, String ext) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return userId + "/" + date + "/" + UUID.randomUUID() + "." + ext;
    }

    private String detectContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
