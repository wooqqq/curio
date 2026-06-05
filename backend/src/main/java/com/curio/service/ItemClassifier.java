package com.curio.service;

import com.curio.dto.item.ClassificationResult;
import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 아이템에 AI 분류 결과(category + tags)를 채운다.
 * 신규 저장(ItemProcessor)과 기존 아이템 재분류(ItemService) 양쪽에서 재사용.
 * item 영속화는 호출자가 책임진다 (여기선 item 필드만 갱신).
 */
@Service
@RequiredArgsConstructor
public class ItemClassifier {

    /** 이 길이 미만의 텍스트는 분류 의미가 적어 AI 호출 없이 ETC 처리. */
    private static final int MIN_TEXT_LENGTH_FOR_AI = 20;

    private final OpenAiService openAiService;
    private final TagRepository tagRepository;

    public void classify(Item item) {
        // 이미지는 분류할 텍스트가 없으니 AI 호출 없이 ETC.
        if (item.getType() == ItemType.IMAGE) {
            item.updateCategory(Category.ETC);
            return;
        }
        // 너무 짧은 텍스트는 비용만 들고 결과도 부정확해 ETC.
        if (item.getType() == ItemType.TEXT
                && (item.getContent() == null || item.getContent().length() < MIN_TEXT_LENGTH_FOR_AI)) {
            item.updateCategory(Category.ETC);
            return;
        }

        // 키가 없으면 분류를 미룬다 — category=null로 남겨, 키를 켠 뒤 reclassify-all이 채우게 한다.
        // (여기서 ETC로 굳히면 키를 켜도 백필이 못 잡아 영영 ETC에 갇힌다.)
        if (!openAiService.isEnabled()) {
            return;
        }

        ClassificationResult result = openAiService.classify(item.getTitle(), item.getContent());
        item.updateCategory(result.category());
        for (String name : result.tags()) {
            item.addTag(getOrCreateTag(name));
        }
    }

    private Tag getOrCreateTag(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    try {
                        // saveAndFlush로 지금 insert를 강제해, 동시 처리 충돌을 여기서 잡는다.
                        return tagRepository.saveAndFlush(Tag.builder().name(name).build());
                    } catch (DataIntegrityViolationException e) {
                        // 다른 트랜잭션이 같은 태그를 먼저 만든 경우 → 그걸 다시 읽어 재사용.
                        return tagRepository.findByName(name).orElseThrow(() -> e);
                    }
                });
    }
}
