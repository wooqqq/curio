package com.curio.dto.item;

import com.curio.entity.Item;
import com.curio.entity.enums.Category;
import com.curio.entity.enums.ItemStatus;
import com.curio.entity.enums.ItemType;

import java.time.LocalDateTime;
import java.util.List;

public record ItemResponse(
        Long id,
        ItemType type,
        String title,
        String content,
        String thumbnailUrl,
        String originalUrl,
        Category category,
        ItemStatus status,
        String aiSummary,
        List<String> tags,
        LocalDateTime createdAt
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getTitle(),
                item.getContent(),
                item.getThumbnailUrl(),
                item.getOriginalUrl(),
                item.getCategory(),
                item.getStatus(),
                item.getAiSummary(),
                item.getTags().stream().map(t -> t.getName()).toList(),
                item.getCreatedAt()
        );
    }
}
