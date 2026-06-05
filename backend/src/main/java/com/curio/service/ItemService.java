package com.curio.service;

import com.curio.dto.item.ItemResponse;
import com.curio.dto.item.OgData;
import com.curio.entity.Item;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemType;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
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
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CurioException(ErrorCode.ITEM_NOT_FOUND));

        if (!item.getUser().getId().equals(userId)) {
            throw new CurioException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (item.getType() != ItemType.LINK || item.getOriginalUrl() == null) {
            throw new CurioException(ErrorCode.RECRAWL_NOT_SUPPORTED);
        }

        OgData og = ogCrawlerService.crawl(item.getOriginalUrl());
        item.updateLinkMetadata(og.title(), og.description(), og.thumbnailUrl());
        return ItemResponse.from(item);
    }

    /**
     * 아직 분류되지 않은(category=null) 아이템을 AI로 일괄 재분류한다.
     * AI 분류 기능을 나중에 켰을 때, 그 전에 쌓인 아이템을 채우는 백필용. 처리한 개수를 반환.
     */
    @Transactional
    public int reclassifyAll(Long userId) {
        List<Item> items = itemRepository.findByUserIdAndCategoryIsNull(userId);
        items.forEach(itemClassifier::classify);
        return items.size();
    }
}
