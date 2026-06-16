package com.curio.service;

import com.curio.common.TextUtils;
import com.curio.dto.item.OgData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class OgCrawlerService {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_ATTEMPTS = 2;
    // 일부 사이트가 봇 UA를 차단하므로 실제 브라우저 UA 사용
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public OgData crawl(String url) {
        // 유튜브는 JS 렌더링 페이지라 일반 스크래핑이 느리고(타임아웃) 제목도 못 읽는다.
        // oEmbed API로 제목/썸네일을 빠르고 안정적으로 가져온다. 실패하면 일반 크롤로 폴백.
        if (isYouTube(url)) {
            OgData yt = crawlYouTube(url);
            if (yt != null) return yt;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();

                String title = meta(doc, "og:title");
                if (title == null) title = meta(doc, "twitter:title");
                if (title == null || title.isBlank()) title = doc.title();
                if (title == null || title.isBlank()) title = titleFromUrl(url);

                String thumbnail = meta(doc, "og:image");
                if (thumbnail == null) thumbnail = meta(doc, "twitter:image");

                String description = meta(doc, "og:description");
                if (description == null) description = meta(doc, "twitter:description");

                return new OgData(trim(title, 500), trim(thumbnail, 1000), trim(description, 1000));
            } catch (Exception e) {
                lastError = e;
                log.warn("OG crawl attempt {}/{} failed for {}: {}", attempt, MAX_ATTEMPTS, url, e.getMessage());
            }
        }
        // 크롤링 자체가 실패해도 raw URL 대신 슬러그에서 읽을 수 있는 제목을 복원
        log.warn("OG crawl gave up for {}: {}", url, lastError != null ? lastError.getMessage() : "unknown");
        return new OgData(trim(titleFromUrl(url), 500), null, null);
    }

    /**
     * OG 크롤링 실패/제목 부재 시 URL 슬러그에서 제목을 추정한다.
     * 예: ".../%EA%BC%BC%EC%88%98%EB%A1%9C-...-b34ee4cc2bc2" → "꼼수로 ..."
     * 슬러그가 무의미(숫자/너무 짧음)하면 호스트명으로 폴백.
     * package-private: 순수 문자열 로직이라 같은 패키지 테스트(OgCrawlerServiceTest)로 고정한다.
     */
    String titleFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "") : url;
            String path = uri.getPath();
            if (path == null || path.isBlank()) return host;

            String slug = "";
            for (String seg : path.split("/")) {
                if (!seg.isBlank()) slug = seg; // 마지막 비어있지 않은 세그먼트
            }
            if (slug.isBlank()) return host;

            slug = URLDecoder.decode(slug, StandardCharsets.UTF_8);
            slug = slug.replaceAll("\\.(html?|php|aspx?)$", "");   // 확장자 제거
            slug = slug.replaceAll("-[0-9a-f]{6,}$", "");          // Medium 등 끝 해시 id 제거
            slug = slug.replaceAll("[-_]+", " ").trim();           // 하이픈/언더스코어 → 공백

            // 숫자만 남거나 너무 짧으면 호스트명이 더 유용
            if (slug.isBlank() || slug.matches("\\d+") || slug.length() < 2) return host;
            return slug;
        } catch (Exception e) {
            return url;
        }
    }

    private boolean isYouTube(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("youtu.be") || host.endsWith(".youtu.be")
                    || host.equals("youtube.com") || host.endsWith(".youtube.com");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 유튜브 oEmbed로 제목·썸네일을 가져온다.
     * 비공개/삭제 영상이거나 응답이 비정상이면 null을 반환해 일반 크롤로 폴백시킨다.
     */
    private OgData crawlYouTube(String url) {
        try {
            String endpoint = "https://www.youtube.com/oembed?format=json&url="
                    + URLEncoder.encode(url, StandardCharsets.UTF_8);
            String body = Jsoup.connect(endpoint)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();

            JsonNode node = MAPPER.readTree(body);
            String title = node.path("title").asText(null);
            String thumbnail = node.path("thumbnail_url").asText(null);
            if (title == null || title.isBlank()) return null;

            String author = node.path("author_name").asText(null);
            String description = (author == null || author.isBlank()) ? null : author;
            return new OgData(trim(title, 500), trim(thumbnail, 1000), trim(description, 1000));
        } catch (Exception e) {
            log.warn("YouTube oEmbed failed for {}: {} — falling back to crawl", url, e.getMessage());
            return null;
        }
    }

    private String meta(Document doc, String property) {
        String val = doc.select("meta[property=" + property + "]").attr("content");
        if (!val.isBlank()) return val;
        val = doc.select("meta[name=" + property + "]").attr("content");
        return val.isBlank() ? null : val;
    }

    // surrogate pair(이모지)를 반토막 내지 않도록 TextUtils.truncate에 위임한다.
    private String trim(String value, int maxLen) {
        return TextUtils.truncate(value, maxLen);
    }
}