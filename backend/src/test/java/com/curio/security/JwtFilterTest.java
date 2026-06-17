package com.curio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JwtFilter 직접 단위 테스트 — 토큰을 SecurityContext의 principal(userId)로 옮기는 인증 핵심 경로.
 * 슬라이스 테스트(@WebMvcTest)는 authentication()로 이 필터를 우회하므로 여기서 직접 검증한다.
 * 어떤 경우든 체인은 계속 진행되고(인증 실패 = 익명 → 이후 401은 JwtAuthenticationEntryPoint 담당, #24),
 * 인증 성공 시에만 SecurityContext에 principal이 채워진다.
 */
class JwtFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-access-secret-key-must-be-at-least-32-bytes-long-1234567890",
            "test-refresh-secret-key-must-be-at-least-32-bytes-long-0987654321",
            60_000L, 600_000L);
    private final JwtFilter filter = new JwtFilter(jwtUtil);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_Bearer토큰이면_userId를_principal로_넣는다() throws Exception {
        String token = jwtUtil.generateAccessToken(99L);
        FilterChain chain = doFilterWith("Bearer " + token);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(99L);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void Authorization헤더가_없으면_인증하지_않고_체인을_진행한다() throws Exception {
        FilterChain chain = doFilterWith(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void Bearer형식이_아니면_인증하지_않는다() throws Exception {
        FilterChain chain = doFilterWith("Basic dXNlcjpwYXNz");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void 유효하지_않은_토큰이면_인증하지_않는다() throws Exception {
        FilterChain chain = doFilterWith("Bearer not-a-real-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void 만료된_토큰이면_인증하지_않는다() throws Exception {
        String expired = new JwtUtil(
                "test-access-secret-key-must-be-at-least-32-bytes-long-1234567890",
                "test-refresh-secret-key-must-be-at-least-32-bytes-long-0987654321",
                -1_000L, 600_000L).generateAccessToken(1L);
        FilterChain chain = doFilterWith("Bearer " + expired);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    /** 주어진 Authorization 헤더로 필터를 한 번 태우고, 검증에 쓸 FilterChain 목을 돌려준다. */
    private FilterChain doFilterWith(String authorizationHeader) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn(authorizationHeader);

        filter.doFilterInternal(request, response, chain);
        return chain;
    }
}
