package com.curio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * [임시 진단 — 측정 후 제거할 것] 카카오 스킬 요청의 '원본 본문'을 로그로 남긴다.
 *
 * 목적: 카톡으로 사진을 보냈을 때 오픈빌더가 스킬 서버에 무엇을(어떤 필드로) 넘기는지 실측한다(이미지 첨부 저장 ① 단계).
 * KakaoSkillRequest DTO는 @JsonIgnoreProperties(ignoreUnknown=true)라 이미지 필드가 파싱 단계에서 버려지므로,
 * 파싱된 객체가 아니라 '원본 JSON'을 봐야 한다. 그래서 ContentCachingRequestWrapper로 본문을 캐시해 사후 로깅한다.
 *
 * ※ 사용자 발화 본문이 로그에 남으므로 상시 운영 금지. 페이로드 구조 파악되면 이 필터는 삭제한다.
 */
@Slf4j
@Component
public class KakaoPayloadLoggingFilter extends OncePerRequestFilter {

    /** base64 인라인 이미지 등 거대한 본문이 로그를 도배하지 않도록 상한. 구조·필드명 파악엔 충분. */
    private static final int MAX_LOG_CHARS = 4000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().contains("/api/v1/kakao/skill")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 다운스트림(@RequestBody 변환기)이 본문을 읽으면 그 바이트가 래퍼에 캐시된다 → 사후에 꺼내 로깅.
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            String payload = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (payload.isEmpty()) {
                log.info("[KAKAO RAW PAYLOAD] (empty body)");
            } else if (payload.length() > MAX_LOG_CHARS) {
                log.info("[KAKAO RAW PAYLOAD] (len={}, truncated) {}", payload.length(), payload.substring(0, MAX_LOG_CHARS));
            } else {
                log.info("[KAKAO RAW PAYLOAD] {}", payload);
            }
        }
    }
}
