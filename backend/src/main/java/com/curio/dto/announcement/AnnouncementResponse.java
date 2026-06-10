package com.curio.dto.announcement;

import com.curio.entity.Announcement;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(
                a.getId(), a.getTitle(), a.getContent(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
