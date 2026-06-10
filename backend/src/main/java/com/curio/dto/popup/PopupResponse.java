package com.curio.dto.popup;

import com.curio.entity.Popup;

import java.time.LocalDateTime;

public record PopupResponse(
        Long id,
        String title,
        String content,
        String imageUrl,
        String linkUrl,
        boolean active,
        LocalDateTime createdAt
) {
    public static PopupResponse from(Popup p) {
        return new PopupResponse(
                p.getId(), p.getTitle(), p.getContent(), p.getImageUrl(),
                p.getLinkUrl(), p.isActive(), p.getCreatedAt());
    }
}