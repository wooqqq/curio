package com.curio.service;

import com.curio.common.TextUtils;
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
import java.util.Base64;
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

    private static final String VISION_PROMPT = """
            You are a content classifier for a Korean developer's personal archive service.
            Look at the image and return JSON in this exact format:
            {"title": "한 줄 제목", "category": "DEVELOPMENT", "tags": ["태그1", "태그2"]}

            title: a short, natural Korean caption (one line, at most 40 characters) describing
            what the image shows. Be concrete (e.g. "스프링 시큐리티 필터 체인 다이어그램").
            Categories (pick exactly one):
            - DEVELOPMENT: programming, tech, tools, frameworks, CS concepts, code/terminal/diagram screenshots
            - CAREER: job-seeking and career — job postings, recruitment, salary, resume(자소서), interview, portfolio, career tips
            - ETC: anything else
            tags: 1-5 short Korean or English keywords relevant to the image.
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
            // 텍스트 한 파트만 담은 contents
            Map<String, Object> textPart = Map.of("text", userInput);
            String responseText = callGemini(SYSTEM_PROMPT, List.of(textPart));
            return parseResult(responseText);
        } catch (Exception e) {
            log.error("Gemini classification failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 이미지를 비전 멀티모달로 분류한다 — 한 줄 제목(캡션) + category + tags를 한 번의 호출로(설계결정 #32).
     * 텍스트 분류와 마찬가지로 <b>실패 시 null</b>을 돌려 호출자가 미분류로 남기게 한다.
     * @param mimeType "image/jpeg" 등 — Gemini inline_data가 요구
     */
    public ClassificationResult classifyImage(byte[] imageBytes, String mimeType) {
        if (!StringUtils.hasText(apiKey) || imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            // 이미지 파트(base64) + 지시 텍스트 한 줄. 분류 기준·출력 형식은 system_instruction(VISION_PROMPT)이 담당.
            Map<String, Object> imagePart = Map.of(
                    "inline_data", Map.of(
                            "mime_type", StringUtils.hasText(mimeType) ? mimeType : "image/jpeg",
                            "data", Base64.getEncoder().encodeToString(imageBytes)));
            Map<String, Object> textPart = Map.of("text", "Classify this image.");
            String responseText = callGemini(VISION_PROMPT, List.of(imagePart, textPart));
            return parseResult(responseText);
        } catch (Exception e) {
            log.error("Gemini image classification failed: {}", e.getMessage());
            return null;
        }
    }

    /** system_instruction + contents의 parts를 받아 generateContent를 호출하고 응답 텍스트를 돌려준다. */
    private String callGemini(String systemPrompt, List<Map<String, Object>> parts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 키를 URL 쿼리(?key=) 대신 헤더로 — 로그·히스토리 노출 방지
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("parts", parts)),
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
        List<?> responseParts = (List<?>) contentNode.get("parts");
        return (String) ((Map<?, ?>) responseParts.get(0)).get("text");
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

        // title은 비전 응답에만 있다(텍스트 분류 응답엔 없어 null). 공백이면 null로 둬 호출자가 폴백 제목을 쓰게.
        JsonNode titleNode = root.get("title");
        String title = (titleNode != null && !titleNode.asText().isBlank())
                ? titleNode.asText().trim() : null;

        return new ClassificationResult(category, new ArrayList<>(tags), title);
    }

    private String buildInput(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(title)) sb.append("Title: ").append(title).append("\n");
        if (StringUtils.hasText(content)) {
            String trimmed = TextUtils.truncate(content, 1000);
            sb.append("Content: ").append(trimmed);
        }
        return sb.toString();
    }
}
