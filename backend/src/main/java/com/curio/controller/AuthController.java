package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = "카카오 OAuth2 로그인 시작점. 브라우저로 직접 접근.")
    @GetMapping("/kakao")
    public void kakaoLogin() {
        // Spring Security OAuth2가 /oauth2/authorization/kakao 로 리다이렉트
    }

    @Operation(summary = "액세스 토큰 재발급")
    @PostMapping("/reissue")
    public ApiResponse<Map<String, String>> reissue(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        String newAccessToken = authService.reissueAccessToken(refreshToken);
        return ApiResponse.success(Map.of("accessToken", newAccessToken));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ApiResponse.success();
    }
}
