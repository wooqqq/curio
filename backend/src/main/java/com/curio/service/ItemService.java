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

    public Page<ItemResponse> getItems(Long userId, Category category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (category != null) {
            return itemRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable)
                    .map(ItemResponse::from);
        }
        return itemRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(ItemResponse::from);
    }
}
