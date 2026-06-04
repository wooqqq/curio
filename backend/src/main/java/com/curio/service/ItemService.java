package com.curio.service;

import com.curio.dto.item.ItemResponse;
import com.curio.entity.enums.Category;
import com.curio.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    public Page<ItemResponse> getItems(Long userId, Category category, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        return itemRepository.search(userId, category, keyword, pageable)
                .map(ItemResponse::from);
    }
}
