package com.curio.service;

import com.curio.common.TextUtils;
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

    private final GeminiService geminiService;
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
        if (!geminiService.isEnabled()) {
            return;
        }

        // 호출 실패(null)면 category를 굳히지 않고 미분류(null)로 둔다 — 백필이 다시 잡게.
        // (ETC로 박으면 일시적 실패가 영구 오염되고, 백필도 진짜 기타와 구분 못 한다.)
        ClassificationResult result = geminiService.classify(item.getTitle(), item.getContent());
        if (result == null) {
            return;
        }
        item.updateCategory(result.category());
        for (String name : result.tags()) {
            item.addTag(getOrCreateTag(name));
        }
    }

    private Tag getOrCreateTag(String rawName) {
        // 태그명도 컬럼 길이(Tag.NAME_MAX)를 넘으면 저장 시 잘리므로, 조회·생성 모두 잘린 값으로 통일해
        // findByName(원본)과 저장값(잘린값)이 어긋나 재조회가 빗나가는 일을 막는다.
        String name = TextUtils.truncate(rawName, Tag.NAME_MAX);
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
