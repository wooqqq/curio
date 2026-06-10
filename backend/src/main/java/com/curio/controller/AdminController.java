package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.dto.announcement.AnnouncementRequest;
import com.curio.dto.announcement.AnnouncementResponse;
import com.curio.dto.popup.PopupRequest;
import com.curio.dto.popup.PopupResponse;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.security.AdminGuard;
import com.curio.service.AnnouncementService;
import com.curio.service.PopupService;
import com.curio.service.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 관리자 전용 — 공지/팝업 관리 + 팝업 이미지 업로드.
 * 모든 엔드포인트 진입부에서 AdminGuard로 ADMIN_KAKAO_IDS allowlist를 검증한다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminGuard adminGuard;
    private final AnnouncementService announcementService;
    private final PopupService popupService;
    private final S3Service s3Service;

    /** 프론트 관리자 가드용 — 현재 로그인 유저가 관리자인지 여부. */
    @GetMapping("/check")
    public ApiResponse<Map<String, Boolean>> check(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("admin", adminGuard.isAdmin(userId)));
    }

    // --- 공지 ---

    @PostMapping("/announcements")
    public ApiResponse<AnnouncementResponse> createAnnouncement(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AnnouncementRequest request) {
        adminGuard.verify(userId);
        return ApiResponse.success(announcementService.create(request));
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementRequest request) {
        adminGuard.verify(userId);
        return ApiResponse.success(announcementService.update(id, request));
    }

    @DeleteMapping("/announcements/{id}")
    public ApiResponse<Void> deleteAnnouncement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        adminGuard.verify(userId);
        announcementService.delete(id);
        return ApiResponse.success();
    }

    // --- 팝업 ---

    @GetMapping("/popups")
    public ApiResponse<List<PopupResponse>> listPopups(@AuthenticationPrincipal Long userId) {
        adminGuard.verify(userId);
        return ApiResponse.success(popupService.list());
    }

    @PostMapping("/popups")
    public ApiResponse<PopupResponse> createPopup(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PopupRequest request) {
        adminGuard.verify(userId);
        return ApiResponse.success(popupService.create(request));
    }

    @PutMapping("/popups/{id}")
    public ApiResponse<PopupResponse> updatePopup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody PopupRequest request) {
        adminGuard.verify(userId);
        return ApiResponse.success(popupService.update(id, request));
    }

    @DeleteMapping("/popups/{id}")
    public ApiResponse<Void> deletePopup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        adminGuard.verify(userId);
        popupService.delete(id);
        return ApiResponse.success();
    }

    // --- 이미지 업로드 ---

    /** 팝업 배너 이미지 업로드 → 공개 URL 반환. 반환된 url을 팝업 imageUrl로 저장한다. */
    @PostMapping("/upload-image")
    public ApiResponse<Map<String, String>> uploadImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        adminGuard.verify(userId);
        if (file == null || file.isEmpty()) {
            throw new CurioException(ErrorCode.INVALID_IMAGE);
        }
        // 헤더는 싼 1차 필터일 뿐, 실제 타입 검증은 S3Service가 magic byte로 수행한다.
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new CurioException(ErrorCode.INVALID_IMAGE);
        }
        try {
            String url = s3Service.uploadImage(file.getBytes(), userId);
            return ApiResponse.success(Map.of("url", url));
        } catch (IOException e) {
            throw new CurioException(ErrorCode.UPLOAD_FAILED);
        }
    }
}
