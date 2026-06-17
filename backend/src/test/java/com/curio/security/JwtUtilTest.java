package com.curio.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 직접 단위 테스트 — 토큰 발급·검증의 핵심 경로(인증의 뿌리).
 * 슬라이스 테스트(@WebMvcTest)는 authentication()로 우회해 이 로직을 안 태우므로 여기서 직접 고정한다.
 * HS256은 256비트(32바이트) 이상 키를 요구해 시크릿을 충분히 길게 둔다.
 */
class JwtUtilTest {

    private static final String ACCESS_SECRET = "test-access-secret-key-must-be-at-least-32-bytes-long-1234567890";
    private static final String REFRESH_SECRET = "test-refresh-secret-key-must-be-at-least-32-bytes-long-0987654321";
    private static final long ACCESS_EXP = 60_000L;
    private static final long REFRESH_EXP = 600_000L;

    private final JwtUtil jwtUtil = new JwtUtil(ACCESS_SECRET, REFRESH_SECRET, ACCESS_EXP, REFRESH_EXP);

    @Test
    void access토큰_발급_후_userId를_복원한다() {
        String token = jwtUtil.generateAccessToken(42L);

        assertThat(jwtUtil.validateAccessToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void refresh토큰_발급_후_userId를_복원한다() {
        String token = jwtUtil.generateRefreshToken(7L);

        assertThat(jwtUtil.validateRefreshToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromRefreshToken(token)).isEqualTo(7L);
    }

    @Test
    void access토큰은_refresh키로_검증되지_않는다() { // 키 격리 — access/refresh 혼용 차단
        String access = jwtUtil.generateAccessToken(1L);

        assertThat(jwtUtil.validateRefreshToken(access)).isFalse();
    }

    @Test
    void refresh토큰은_access키로_검증되지_않는다() {
        String refresh = jwtUtil.generateRefreshToken(1L);

        assertThat(jwtUtil.validateAccessToken(refresh)).isFalse();
    }

    @Test
    void 변조된_토큰은_검증에_실패한다() {
        String token = jwtUtil.generateAccessToken(1L);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(jwtUtil.validateAccessToken(tampered)).isFalse();
    }

    @Test
    void JWT가_아닌_문자열은_검증에_실패한다() {
        assertThat(jwtUtil.validateAccessToken("not-a-jwt")).isFalse();
        assertThat(jwtUtil.validateAccessToken("")).isFalse();
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        // 만료시간을 음수로 둬 발급 즉시 만료된 토큰을 만든다.
        JwtUtil expiredUtil = new JwtUtil(ACCESS_SECRET, REFRESH_SECRET, -1_000L, REFRESH_EXP);
        String expired = expiredUtil.generateAccessToken(1L);

        assertThat(jwtUtil.validateAccessToken(expired)).isFalse();
    }

    @Test
    void 같은_유저를_연속_발급해도_jti로_토큰이_겹치지_않는다() { // 설계결정 #11 / 동일 초 중복키 회귀 방어
        String t1 = jwtUtil.generateAccessToken(1L);
        String t2 = jwtUtil.generateAccessToken(1L);

        assertThat(t1).isNotEqualTo(t2);
    }
}
