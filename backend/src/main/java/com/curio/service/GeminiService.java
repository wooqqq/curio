package com.curio.service;

import com.curio.dto.item.ClassificationResult;
import com.curio.entity.enums.Category;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini(Google AI Studio)로 아이템을 분류한다(category + tags).
 * OpenAI에서 전환 — 무료티어로 비용 0(설계결정 #19). 인터페이스/파이프라인은 그대로,
 * HTTP 호출만 Gemini generateContent로 바꿨다. 키 없으면 호출자가 분류를 미룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private static final String SYSTEM_PROMPT = """
            You are a content classifier for a Korean developer's personal archive service.
            Classify the given content and return JSON in this exact format:
            {"category": "DEVELOPMENT", "tags": ["태그1", "태그2", "태그3"]}

            Categories (pick exactly one):
            - DEVELOPMENT: programming, tech, tools, frameworks, CS concepts
            - CAREER: anything about job-seeking and career — job postings, recruitment, salary, company info, resume(자소서), cover letter, interview, portfolio, networking, career tips, soft skills
            - ETC: anything else

            Tags: 1-5 short Korean or English keywords relevant to the content.
            Return ONLY valid JSON, no explanation.
            """;

    /** API 키가 설정돼 실제 분류가 가능한지. 키가 없으면 호출자가 분류를 미룬다. */
    public boolean isEnabled() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * 분류 결과를 반환한다. <b>호출 실패 시 null</b>을 반환해, 호출자가 카테고리를
     * 굳히지 않고 미분류(null)로 남기게 한다 — 일시적 실패(429 등)가 ETC로 잘못 박혀
     * 영구 오염되는 걸 막는다. ETC는 "진짜 기타"일 때만 나온다.
     */
    public ClassificationResult classify(String title, String content) {
        if (!StringUtils.hasText(apiKey)) {
            log.debug("Gemini API key not configured, skipping classification");
            return null;
        }

        String userInput = buildInput(title, content);
        try {
            String responseText = callGemini(userInput);
            return parseResult(responseText);
        } catch (Exception e) {
            log.error("Gemini classification failed: {}", e.getMessage());
            return null;
        }
    }

    private String callGemini(String userInput) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 키를 URL 쿼리(?key=) 대신 헤더로 — 로그·히스토리 노출 방지
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userInput)))),
                // 응답을 순수 JSON으로 강제 (OpenAI의 response_format json_object 대응)
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = BASE_URL + model + ":generateContent";
        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null) throw new RuntimeException("empty response from Gemini");

        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("no candidates in Gemini response (blocked/empty)");
        }
        Map<?, ?> first = (Map<?, ?>) candidates.get(0);
        Map<?, ?> contentNode = (Map<?, ?>) first.get("content");
        List<?> parts = (List<?>) contentNode.get("parts");
        return (String) ((Map<?, ?>) parts.get(0)).get("text");
    }

    // package-private: 응답 파싱 로직을 GeminiServiceTest로 직접 검증한다.
    ClassificationResult parseResult(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        Category category = Category.ETC;
        try {
            category = Category.valueOf(root.get("category").asText());
        } catch (Exception ignored) {}

        // LinkedHashSet: 첫 등장 순서를 유지하면서 중복 제거 (LLM이 같은 태그를 여러 번 반환해도
        // item_tags에 중복 조인 행/중복 표시가 쌓이지 않게 한다).
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JsonNode tagsNode = root.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                String t = tag.asText().trim();
                if (!t.isBlank() && t.length() <= 50) tags.add(t);
            }
        }

        return new ClassificationResult(category, new ArrayList<>(tags));
    }

    private String buildInput(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(title)) sb.append("Title: ").append(title).append("\n");
        if (StringUtils.hasText(content)) {
            String trimmed = content.length() > 1000 ? content.substring(0, 1000) : content;
            sb.append("Content: ").append(trimmed);
        }
        return sb.toString();
    }
}
