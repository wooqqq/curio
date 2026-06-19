package com.curio.service;

import com.curio.common.TextUtils;
import com.curio.dto.item.ItemResponse;
import com.curio.dto.item.OgData;
import com.curio.dto.item.UpdateItemRequest;
import com.curio.entity.Item;
import com.curio.entity.Tag;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.processor.ItemProcessor;
import com.curio.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final OgCrawlerService ogCrawlerService;
    private final ItemClassifier itemClassifier;
    private final ItemProcessor itemProcessor;
    private final TagService tagService;

    /**
     * 웹앱에서 링크를 직접 추가한다(봇 없이 저장). 동기 처리 후 저장된 아이템을 반환.
     * 클래스 기본값이 readOnly라, 쓰기 트랜잭션을 위해 메서드에 @Transactional을 다시 건다.
     */
    @Transactional
    public ItemResponse addLink(Long userId, String url) {
        return itemProcessor.addLink(userId, url);
    }

    public Page<ItemResponse> getItems(Long userId, Category category, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        return itemRepository.search(userId, category, keyword, pageable)
                .map(ItemResponse::from);
    }

    /**
     * OG 메타데이터를 다시 크롤링해 제목/요약/썸네일을 갱신한다.
     * 저장 당시 크롤링이 실패해 제목이 깨진 LINK 아이템 복구용.
     */
    @Transactional
    public ItemResponse recrawl(Long userId, Long itemId) {
        Item item = loadOwned(userId, itemId);
        if (item.getType() != ItemType.LINK || item.getOriginalUrl() == null) {
            throw new CurioException(ErrorCode.RECRAWL_NOT_SUPPORTED);
        }

        OgData og = ogCrawlerService.crawl(item.getOriginalUrl());
        item.updateLinkMetadata(og.title(), og.description(), og.thumbnailUrl());
        return ItemResponse.from(item);
    }

    /**
     * 아이템의 제목/메모/카테고리를 부분 수정한다. 본인 소유가 아니면 ITEM_NOT_FOUND.
     * title이 공백만이면 INVALID_INPUT. 제목·카테고리를 고치면 이후 재크롤/재분류가 덮어쓰지 않게 표시된다(#31·#33).
     */
    @Transactional
    public ItemResponse update(Long userId, Long itemId, UpdateItemRequest request) {
        Item item = loadOwned(userId, itemId);

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new CurioException(ErrorCode.INVALID_INPUT);
            }
            item.editTitle(title);
        }
        if (request.memo() != null) {
            item.updateMemo(request.memo());
        }
        if (request.category() != null) {
            item.editCategory(request.category());
        }
        return ItemResponse.from(item);
    }

    /** 아이템에 태그를 추가한다(사용자 수동). 빈 이름이면 INVALID_INPUT. 본인 소유 아니면 ITEM_NOT_FOUND. */
    @Transactional
    public ItemResponse addTag(Long userId, Long itemId, String name) {
        Item item = loadOwned(userId, itemId);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new CurioException(ErrorCode.INVALID_INPUT);
        }
        item.addTag(tagService.getOrCreate(trimmed)); // addTag는 멱등 — 중복이면 무시
        return ItemResponse.from(item);
    }

    /** 아이템에서 태그를 뗀다(join 행만 제거, 공유 Tag는 유지). 본인 소유 아니면 ITEM_NOT_FOUND. */
    @Transactional
    public ItemResponse removeTag(Long userId, Long itemId, String name) {
        Item item = loadOwned(userId, itemId);
        // 저장된 태그명은 NAME_MAX로 잘려 있으므로, 떼낼 때도 같은 기준으로 맞춰 비교한다.
        item.removeTagByName(TextUtils.truncate(name == null ? "" : name.trim(), Tag.NAME_MAX));
        return ItemResponse.from(item);
    }

    /**
     * 아이템을 삭제한다. 본인 소유가 아니면 ITEM_NOT_FOUND (존재 여부를 노출하지 않는다).
     * item_tags 조인 행은 함께 지워지고, 공유 자원인 Tag 자체는 남는다.
     */
    @Transactional
    public void delete(Long userId, Long itemId) {
        itemRepository.delete(loadOwned(userId, itemId));
    }

    /** 아이템을 찾고 본인 소유인지 검증한다. 없거나 남의 것이면 ITEM_NOT_FOUND(존재 비노출). */
    private Item loadOwned(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CurioException(ErrorCode.ITEM_NOT_FOUND));
        if (!item.getUser().getId().equals(userId)) {
            throw new CurioException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 미분류(category=null) 또는 ETC 아이템을 AI로 일괄 재분류한다.
     * AI 키/모델을 켜거나 고친 뒤, 그 전에 미분류로 쌓였거나 호출 실패로 ETC에 박힌
     * 아이템을 채우는 백필용. 기존 태그는 비우고 새로 분류한다. 처리한 개수를 반환.
     */
    @Transactional
    public int reclassifyAll(Long userId) {
        List<Item> items = itemRepository.findForReclassify(userId, Category.ETC);
        items.forEach(item -> {
            item.clearTags();
            itemClassifier.classify(item);
        });
        return items.size();
    }
}
