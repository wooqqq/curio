package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.service.AuthService;
import com.curio.service.KakaoAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KakaoAuthService kakaoAuthService;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${kakao.authorization-uri}")
    private String kakaoAuthorizationUri;

    @Operation(summary = "카카오 로그인 URL 반환")
    @GetMapping("/kakao/login-url")
    public ApiResponse<String> kakaoLoginUrl() {
        String url = UriComponentsBuilder.fromUriString(kakaoAuthorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", kakaoRedirectUri)
                .build().toUriString();
        return ApiResponse.success(url);
    }

    @Operation(summary = "카카오 OAuth2 콜백")
    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam String code,
                              HttpServletResponse response) throws IOException {
        Map<String, String> tokens = kakaoAuthService.login(code);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(kakaoAuthService.getFrontendUrl() + "/auth/callback")
                .queryParam("accessToken", tokens.get("accessToken"))
                .queryParam("refreshToken", tokens.get("refreshToken"))
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }

    @Operation(summary = "액세스 토큰 재발급")
    @PostMapping("/reissue")
    public ApiResponse<Map<String, String>> reissue(@RequestBody Map<String, String> body) {
        return ApiResponse.success(authService.reissue(body.get("refreshToken")));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ApiResponse.success();
    }
}
