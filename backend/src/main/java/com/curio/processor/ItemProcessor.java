package com.curio.processor;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemProcessor {

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

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
            case LINK -> processLink(user, text);
            case IMAGE -> processImage(user, text);
            case TEXT -> processText(user, text);
        }
    }

    private void processLink(User user, String url) {
        String normalized = normalizeUrl(url);

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
        String normalized = normalizeUrl(url);

        if (itemRepository.existsByUserAndNormalizedUrl(user, normalized)) {
            throw new CurioException(ErrorCode.DUPLICATE_URL);
        }
        return ItemResponse.from(saveLink(user, url, normalized));
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
                .title(text.length() > 100 ? text.substring(0, 100) + "..." : text)
                .content(text)
                .build();

        itemClassifier.classify(item);
        itemRepository.save(item);
        log.info("TEXT saved: userId={}", user.getId());
    }

    private ItemType detectType(String utterance) {
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

    private String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath().replaceAll("/+$", "") : "";
            return "https://" + host + path;
        } catch (Exception e) {
            return url;
        }
    }
}
