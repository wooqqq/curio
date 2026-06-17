package com.curio.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    /**
     * 외부 HTTP 호출용 공용 RestTemplate (Gemini 분류·카카오 토큰 교환).
     * 타임아웃이 없으면(기존 new RestTemplate()) 동기 경로(웹 addLink)의 Gemini 호출이 무한 대기할 수 있어,
     * 느린 응답 하나가 요청 스레드를 영원히 점유 → 동시 다발 시 풀 고갈(예측 버그 #5)로 이어진다.
     * read 10s: gemini-2.5-flash 분류엔 충분하고, 초과 시 classify가 예외를 잡아 null(미분류)로 강등 →
     * reclassify-all 백필이 나중에 채운다(#21). connect 3s. 카카오 토큰 교환에도 동일 상한이 안전하게 적용된다.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
