package com.curio.security;

import com.curio.dto.ApiResponse;
import com.curio.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 미인증 요청(access 토큰 없음·만료·유효하지 않음)에 401을 돌려준다.
 * Security 기본값은 401이 아니라 403(Http403ForbiddenEntryPoint)이라, 프론트 axios 인터셉터가
 * 401에서만 토큰 재발급(reissue)을 트리거하므로 세션 만료가 조용히 갱신되지 않고 깨지던 문제를 막는다.
 * 인증은 됐으나 권한이 없는 경우(403)는 CurioException으로 따로 처리되어 이 진입점을 타지 않는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
