package com.curio.service;

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
