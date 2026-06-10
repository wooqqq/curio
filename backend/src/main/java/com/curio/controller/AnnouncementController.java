package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.dto.announcement.AnnouncementResponse;
import com.curio.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 공지 조회(로그인 사용자 공통). 팝업의 linkUrl이 /announcements/{id}로 연결된다.
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ApiResponse<List<AnnouncementResponse>> list() {
        return ApiResponse.success(announcementService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AnnouncementResponse> get(@PathVariable Long id) {
        return ApiResponse.success(announcementService.get(id));
    }
}
