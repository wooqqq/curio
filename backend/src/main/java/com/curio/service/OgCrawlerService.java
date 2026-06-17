package com.curio.service;

import com.curio.common.TextUtils;
import com.curio.common.UrlGuard;
import com.curio.exception.CurioException;
import com.curio.dto.item.OgData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class OgCrawlerService {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_REDIRECTS = 5;
    // 크롤 전체(재시도·리다이렉트 포함) wall-clock 상한. SSRF 방어로 리다이렉트를 직접 추적(매 홉 재검증)하면서
    // 최악이 6홉×5s×2회=최대 60s까지 늘 수 있어(예측 버그 #5), 동기 웹 addLink가 그만큼 요청 스레드를
    // 점유하지 않도록 총 예산을 묶는다. 초과하면 남은 홉/재시도를 멈추고 titleFromUrl 폴백으로 떨어진다.
    private static final int CRAWL_BUDGET_MS = 12_000;
    // 일부 사이트가 봇 UA를 차단하므로 실제 브라우저 UA 사용
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public OgData crawl(String url) {
        // SSRF 방지: 서버가 fetch하기 전에 내부/사설 대역으로 향하는 URL을 막는다(BLOCKED_URL).
        UrlGuard.verifyPublic(url);

        // 유튜브는 JS 렌더링 페이지라 일반 스크래핑이 느리고(타임아웃) 제목도 못 읽는다.
        // oEmbed API로 제목/썸네일을 빠르고 안정적으로 가져온다. 실패하면 일반 크롤로 폴백.
        if (isYouTube(url)) {
            OgData yt = crawlYouTube(url);
            if (yt != null) return yt;
        }

        Exception lastError = null;
        long deadline = System.currentTimeMillis() + CRAWL_BUDGET_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (System.currentTimeMillis() >= deadline) {
                log.warn("OG crawl budget exhausted before attempt {} for {}", attempt, url);
                break;
            }
            try {
                Document doc = fetchValidated(url, deadline);

                String title = meta(doc, "og:title");
                if (title == null) title = meta(doc, "twitter:title");
                if (title == null || title.isBlank()) title = doc.title();
                if (title == null || title.isBlank()) title = titleFromUrl(url);

                String thumbnail = meta(doc, "og:image");
                if (thumbnail == null) thumbnail = meta(doc, "twitter:image");

                String description = meta(doc, "og:description");
                if (description == null) description = meta(doc, "twitter:description");

                return new OgData(trim(title, 500), trim(thumbnail, 1000), trim(description, 1000));
            } catch (CurioException e) {
                // BLOCKED_URL(SSRF 차단)은 폴백 없이 그대로 전파한다 — 리다이렉트가 내부 IP를 가리키는 우회를 막기 위함.
                throw e;
            } catch (Exception e) {
                // 네트워크 오류(IOException)뿐 아니라 Jsoup 런타임 예외(예: 잘못된 URL의 IllegalArgumentException)도
                // 폴백시킨다 — 동기 addLink에서 500 대신 titleFromUrl로 우아하게 강등(SSRF용 IOException 한정 이전으로 복원).
                lastError = e;
                log.warn("OG crawl attempt {}/{} failed for {}: {}", attempt, MAX_ATTEMPTS, url, e.getMessage());
            }
        }
        // 크롤링 자체가 실패해도 raw URL 대신 슬러그에서 읽을 수 있는 제목을 복원
        log.warn("OG crawl gave up for {}: {}", url, lastError != null ? lastError.getMessage() : "unknown");
        return new OgData(trim(titleFromUrl(url), 500), null, null);
    }

    /**
     * Jsoup 자동 리다이렉트를 끄고 직접 따라가며 매 홉을 SSRF 검증한다.
     * 공개 URL이 302로 내부 IP를 가리키는 우회를 막기 위함. 2xx면 파싱, 3xx면 Location으로 이어간다.
     * (4xx/5xx는 Jsoup이 HttpStatusException(IOException)으로 던져 호출부에서 폴백 처리.)
     */
    private Document fetchValidated(String startUrl, long deadline) throws IOException {
        String current = startUrl;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            UrlGuard.verifyPublic(current);
            // 남은 예산만큼만 이 홉에 쓴다(전체 데드라인 분배). 이미 초과면 멈추고 폴백으로 떨어진다.
            int remaining = (int) (deadline - System.currentTimeMillis());
            if (remaining <= 0) {
                throw new IOException("crawl budget exceeded: " + current);
            }
            Connection.Response res = Jsoup.connect(current)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .timeout(Math.min(TIMEOUT_MS, remaining))
                    .followRedirects(false)
                    .execute();

            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                String location = res.header("Location");
                if (location == null || location.isBlank()) {
                    throw new IOException("redirect without Location: " + current);
                }
                try {
                    // 상대 경로 Location을 현재 URL 기준으로 절대화
                    current = URI.create(res.url().toString()).resolve(location).toString();
                } catch (RuntimeException e) {
                    throw new IOException("bad redirect location: " + location, e);
                }
                continue;
            }
            return res.parse();
        }
        throw new IOException("too many redirects: " + startUrl);
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