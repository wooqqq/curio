package com.curio.service;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

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
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
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
