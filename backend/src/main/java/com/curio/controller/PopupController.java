package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.dto.popup.PopupResponse;
import com.curio.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 아카이브 진입 시 노출할 활성 팝업 조회. 없으면 data=null.
 */
@RestController
@RequestMapping("/api/v1/popups")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    @GetMapping("/active")
    public ApiResponse<PopupResponse> getActive() {
        return ApiResponse.success(popupService.getActive());
    }
}