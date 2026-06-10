package com.curio.service;

import com.curio.dto.announcement.AnnouncementRequest;
import com.curio.dto.announcement.AnnouncementResponse;
import com.curio.entity.Announcement;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    /** 사용자/관리자 공통 — 최신순 목록. */
    public List<AnnouncementResponse> list() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    public AnnouncementResponse get(Long id) {
        return AnnouncementResponse.from(findOrThrow(id));
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        Announcement saved = announcementRepository.save(
                Announcement.builder()
                        .title(request.title())
                        .content(request.content())
                        .build());
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementRequest request) {
        Announcement announcement = findOrThrow(id);
        announcement.update(request.title(), request.content());
        return AnnouncementResponse.from(announcement);
    }

    @Transactional
    public void delete(Long id) {
        Announcement announcement = findOrThrow(id);
        announcementRepository.delete(announcement);
    }

    private Announcement findOrThrow(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new CurioException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }
}
