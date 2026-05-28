package com.curio.processor;

import com.curio.dto.item.ClassificationResult;
import com.curio.dto.item.OgData;
import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.User;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.ItemRepository;
import com.curio.repository.TagRepository;
import com.curio.repository.UserRepository;
import com.curio.service.OgCrawlerService;
import com.curio.service.OpenAiService;
import com.curio.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemProcessor {

    private static final int MIN_TEXT_LENGTH_FOR_AI = 20;
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final TagRepository tagRepository;
    private final OgCrawlerService ogCrawlerService;
    private final OpenAiService openAiService;
    private final S3Service s3Service;

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

        OgData og = ogCrawlerService.crawl(url);
        ClassificationResult classification = openAiService.classify(og.title(), og.description());

        Item item = Item.builder()
                .user(user)
                .type(ItemType.LINK)
                .title(og.title())
                .content(og.description())
                .thumbnailUrl(og.thumbnailUrl())
                .originalUrl(url)
                .normalizedUrl(normalized)
                .category(classification.category())
                .build();

        attachTags(item, classification.tags());
        itemRepository.save(item);
        log.info("LINK saved: userId={} title={}", user.getId(), og.title());
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
                .category(Category.ETC)
                .build();

        itemRepository.save(item);
        log.info("IMAGE saved: userId={}", user.getId());
    }

    private void processText(User user, String text) {
        ClassificationResult classification = text.length() >= MIN_TEXT_LENGTH_FOR_AI
                ? openAiService.classify(null, text)
                : new ClassificationResult(Category.ETC, List.of());

        Item item = Item.builder()
                .user(user)
                .type(ItemType.TEXT)
                .title(text.length() > 100 ? text.substring(0, 100) + "..." : text)
                .content(text)
                .category(classification.category())
                .build();

        attachTags(item, classification.tags());
        itemRepository.save(item);
        log.info("TEXT saved: userId={}", user.getId());
    }

    private void attachTags(Item item, List<String> tagNames) {
        for (String name : tagNames) {
            Tag tag = tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
            item.addTag(tag);
        }
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
