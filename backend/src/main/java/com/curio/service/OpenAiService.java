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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4.1-mini}")
    private String model;

    private static final String SYSTEM_PROMPT = """
            You are a content classifier for a Korean developer's personal archive service.
            Classify the given content and return JSON in this exact format:
            {"category": "DEVELOPMENT", "tags": ["태그1", "태그2", "태그3"]}

            Categories (pick exactly one):
            - DEVELOPMENT: programming, tech, tools, frameworks, CS concepts
            - CAREER: career tips, portfolio, interview, networking, soft skills
            - JOB: job postings, company info, recruitment, salary
            - ETC: anything else

            Tags: 1-5 short Korean or English keywords relevant to the content.
            Return ONLY valid JSON, no explanation.
            """;

    public ClassificationResult classify(String title, String content) {
        if (!StringUtils.hasText(apiKey)) {
            log.debug("OpenAI API key not configured, using default classification");
            return new ClassificationResult(Category.ETC, List.of());
        }

        String userInput = buildInput(title, content);
        try {
            String responseJson = callOpenAi(userInput);
            return parseResult(responseJson);
        } catch (Exception e) {
            log.error("OpenAI classification failed: {}", e.getMessage());
            return new ClassificationResult(Category.ETC, List.of());
        }
    }

    private String callOpenAi(String userInput) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userInput)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<?, ?> response = restTemplate.postForObject(OPENAI_URL, request, Map.class);

        if (response == null) throw new RuntimeException("empty response from OpenAI");

        List<?> choices = (List<?>) response.get("choices");
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) first.get("message");
        return (String) message.get("content");
    }

    private ClassificationResult parseResult(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        Category category = Category.ETC;
        try {
            category = Category.valueOf(root.get("category").asText());
        } catch (Exception ignored) {}

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = root.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                String t = tag.asText().trim();
                if (!t.isBlank() && t.length() <= 50) tags.add(t);
            }
        }

        return new ClassificationResult(category, tags);
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
