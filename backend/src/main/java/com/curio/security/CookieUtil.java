package com.curio.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * refresh 토큰을 httpOnly 쿠키로 다룬다.
 * access 토큰은 쿠키에 절대 담지 않는다 (프론트 메모리 보관).
 * 쿠키 속성(secure/sameSite/domain)은 프로필별 프로퍼티로 주입 — dev/prod 분리.
 */
@Component
public class CookieUtil {

    public static final String REFRESH_TOKEN = "refreshToken";

    /** auth 엔드포인트로만 쿠키가 전송되도록 경로를 좁힌다 (CSRF 표면 축소). */
    private static final String PATH = "/api/v1/auth";

    @Value("${cookie.secure}")
    private boolean secure;

    @Value("${cookie.same-site}")
    private String sameSite;

    @Value("${cookie.domain:}")
    private String domain;

    /** refresh 토큰 만료(ms)와 동일하게 쿠키 Max-Age를 맞춘다. */
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public ResponseCookie create(String refreshToken) {
        return build(refreshToken, refreshExpirationMs / 1000);
    }

    public ResponseCookie clear() {
        return build("", 0);
    }

    public String resolve(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_TOKEN, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(maxAgeSeconds);
        if (StringUtils.hasText(domain)) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
