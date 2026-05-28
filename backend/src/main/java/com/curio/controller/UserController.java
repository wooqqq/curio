package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.service.LinkCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final LinkCodeService linkCodeService;

    @GetMapping("/link-code")
    public ApiResponse<Map<String, String>> getLinkCode(@AuthenticationPrincipal Long userId) {
        String code = linkCodeService.generateCode(userId);
        return ApiResponse.success(Map.of("code", code));
    }
}
