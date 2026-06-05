package com.curio.controller;

import com.curio.dto.ApiResponse;
import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import com.curio.security.CookieUtil;
import com.curio.service.AuthService;
import com.curio.service.KakaoAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
    private final CookieUtil cookieUtil;

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

        // refresh 토큰은 httpOnly 쿠키로만 전달한다 (URL/JS 노출 차단).
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.create(tokens.get("refreshToken")).toString());

        // access 토큰도 URL에 싣지 않는다 — 프론트가 콜백 직후 /reissue로 받아간다.
        String redirectUrl = UriComponentsBuilder
                .fromUriString(kakaoAuthService.getFrontendUrl() + "/auth/callback")
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }

    @Operation(summary = "액세스 토큰 재발급 (refresh 쿠키 기반)")
    @PostMapping("/reissue")
    public ApiResponse<Map<String, String>> reissue(HttpServletRequest request,
                                                     HttpServletResponse response) {
        String refreshToken = cookieUtil.resolve(request);
        if (refreshToken == null) {
            throw new CurioException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        Map<String, String> tokens = authService.reissue(refreshToken);

        // refresh 쿠키를 같은 값으로 다시 내려 Max-Age만 연장(sliding expiration), access는 body로 반환.
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.create(tokens.get("refreshToken")).toString());
        return ApiResponse.success(Map.of("accessToken", tokens.get("accessToken")));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId,
                                    HttpServletResponse response) {
        authService.logout(userId);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.clear().toString());
        return ApiResponse.success();
    }
}
