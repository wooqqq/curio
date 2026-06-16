package com.curio.processor;

import com.curio.common.TextUtils;
import com.curio.dto.item.ItemResponse;
import com.curio.dto.item.OgData;
import com.curio.entity.Item;
import com.curio.entity.User;
import com.curio.entity.enums.ItemType;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.ItemRepository;
import com.curio.repository.UserRepository;
import com.curio.service.ItemClassifier;
import com.curio.service.OgCrawlerService;
import com.curio.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemProcessor {

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    /**
     * 페이지 식별과 무관한 추적용 쿼리 파라미터. 중복 판정 전에 제거한다(키는 소문자 비교, utm_*는 접두 매칭).
     * 근거: 단일 표준은 없고, 프라이버시 도구(Brave·Firefox·ClearURLs·AdGuard) 공통 목록의 고빈도 항목.
     * 설계결정 #23. 새 추적 파라미터가 보이면 여기에 추가.
     */
    private static final Set<String> TRACKING_PARAMS = Set.of(
            // Google 광고/애널리틱스 (utm_* 접두는 canonicalizeQuery에서 별도 처리)
            "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
            // 기타 광고 클릭 ID
            "fbclid", "msclkid", "yclid", "twclid",
            // 소셜 공유 / 메일·마케팅 자동화
            "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok");

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final OgCrawlerService ogCrawlerService;
    private final S3Service s3Service;
    private final ItemClassifier itemClassifier;

    @Transactional
    public void process(Long userId, String utterance) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CurioException(ErrorCode.USER_NOT_FOUND));

        String text = utterance.trim();
        ItemType type = detectType(text);
        log.info("Processing item for user={} type={}", userId, type);

        switch (type) {
            // 발화에 URL 앞뒤로 텍스트가 섞여도 URL만 뽑아 저장한다(웹 addLink와 동일). TEXT는 전체 보존.
            case LINK -> processLink(user, extractUrl(text));
            case IMAGE -> processImage(user, extractUrl(text));
            case TEXT -> processText(user, text);
        }
    }

    private void processLink(User user, String url) {
        // 저장 시 normalizedUrl이 컬럼 길이로 잘리므로(Item.URL_MAX), 중복 판정도 같은 값으로 해야
        // exists 체크와 저장값이 어긋나지 않는다.
        String normalized = TextUtils.truncate(normalizeUrl(url), Item.URL_MAX);

        if (itemRepository.existsByUserAndNormalizedUrl(user, normalized)) {
            log.info("Duplicate URL skipped: userId={} url={}", user.getId(), normalized);
            return;
        }
        saveLink(user, url, normalized);
    }

    private Item saveLink(User user, String url, String normalized) {
        OgData og = ogCrawlerService.crawl(url);

        Item item = Item.builder()
                .user(user)
                .type(ItemType.LINK)
                .title(og.title())
                .content(og.description())
                .thumbnailUrl(og.thumbnailUrl())
                .originalUrl(url)
                .normalizedUrl(normalized)
                .build();

        itemClassifier.classify(item);
        itemRepository.save(item);
        log.info("LINK saved: userId={} title={} category={}", user.getId(), og.title(), item.getCategory());
        return item;
    }

    /**
     * 웹앱에서 링크를 직접 추가한다. 봇 경로(process)와 달리 동기 처리하고 결과를 반환해
     * 화면에 즉시 카드를 띄울 수 있게 한다. 중복이면 DUPLICATE_URL로 알린다(봇은 조용히 skip).
     */
    @Transactional
    public ItemResponse addLink(Long userId, String input) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CurioException(ErrorCode.USER_NOT_FOUND));

        if (input == null || !URL_PATTERN.matcher(input).find()) {
            throw new CurioException(ErrorCode.INVALID_INPUT, "올바른 링크(http/https)를 입력해주세요.");
        }
        String url = extractUrl(input.trim());
        // 저장 시 잘리는 길이(Item.URL_MAX)에 맞춰 중복 판정 — processLink와 동일.
        String normalized = TextUtils.truncate(normalizeUrl(url), Item.URL_MAX);

        if (itemRepository.existsByUserAndNormalizedUrl(user, normalized)) {
            throw new CurioException(ErrorCode.DUPLICATE_URL);
        }
        try {
            return ItemResponse.from(saveLink(user, url, normalized));
        } catch (DataIntegrityViolationException e) {
            // 위 exists 체크와 insert 사이에 같은 URL이 동시 추가되면(TOCTOU) unique 제약
            // (uk_user_normalized_url)이 터진다. IDENTITY 전략이라 save 시점에 즉시 INSERT돼
            // 여기서 잡힌다. 500 대신 중복으로 알린다.
            throw new CurioException(ErrorCode.DUPLICATE_URL);
        }
    }

    private void processImage(User user, String imageUrl) {
        String s3Key = s3Service.uploadFromUrl(imageUrl, user.getId());
        String thumbnailUrl = s3Key != null ? s3Service.getPublicUrl(s3Key) : imageUrl;

        Item item = Item.builder()
                .user(user)
                .type(ItemType.IMAGE)
                .title("이미지")
                .originalUrl(imageUrl)
                .thumbnailUrl(thumbnailUrl)
                .s3Key(s3Key)
                .build();

        itemClassifier.classify(item);
        itemRepository.save(item);
        log.info("IMAGE saved: userId={}", user.getId());
    }

    private void processText(User user, String text) {
        Item item = Item.builder()
                .user(user)
                .type(ItemType.TEXT)
                .title(text.length() > 100 ? TextUtils.truncate(text, 100) + "..." : text)
                .content(text)
                .build();

        itemClassifier.classify(item);
        itemRepository.save(item);
        log.info("TEXT saved: userId={}", user.getId());
    }

    // package-private: 순수 분기 로직이라 같은 패키지 테스트(ItemProcessorTest)에서 직접 검증한다.
    ItemType detectType(String utterance) {
        if (URL_PATTERN.matcher(utterance).find()) {
            String urlPart = extractUrl(utterance);
            if (IMAGE_URL_PATTERN.matcher(urlPart).matches()) {
                return ItemType.IMAGE;
            }
            return ItemType.LINK;
        }
        return ItemType.TEXT;
    }

    private String extractUrl(String utterance) {
        var matcher = URL_PATTERN.matcher(utterance);
        return matcher.find() ? matcher.group() : utterance;
    }

    // package-private: 중복 판정 기준이라 테스트(ItemProcessorTest)로 동작을 고정한다.
    String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath().replaceAll("/+$", "") : "";
            return "https://" + host + path + canonicalizeQuery(uri.getRawQuery());
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 추적 파라미터(utm_*, fbclid 등)만 걷어내고 의미 있는 쿼리는 보존한다.
     * 예: youtube watch?v=xxx, 검색 ?q=... 는 남겨 서로 다른 페이지가 중복 판정되지 않게 한다.
     * 남는 파라미터가 없으면 빈 문자열을 반환한다(원래 순서 유지).
     */
    private String canonicalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String kept = Arrays.stream(rawQuery.split("&"))
                .filter(p -> !p.isBlank())
                .filter(p -> {
                    String key = p.split("=", 2)[0].toLowerCase();
                    return !key.startsWith("utm_") && !TRACKING_PARAMS.contains(key);
                })
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return kept.isEmpty() ? "" : "?" + kept;
    }
}
