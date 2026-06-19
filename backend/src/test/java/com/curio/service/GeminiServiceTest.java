package com.curio.service;

import com.curio.dto.item.ClassificationResult;
import com.curio.entity.enums.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeminiService.parseResult 단위 테스트.
 * RestTemplate은 파싱에 안 쓰이므로 null, ObjectMapper만 실제 인스턴스로 주입.
 */
class GeminiServiceTest {

    private final GeminiService service = new GeminiService(null, new ObjectMapper());

    @Test
    void parseResult_중복태그를_순서유지하며_제거한다() throws Exception {
        // LLM이 같은 태그를 여러 번 반환하는 상황 (실제로 흔함)
        String json = "{\"category\":\"DEVELOPMENT\",\"tags\":[\"스프링\",\"스프링\",\"JPA\",\"스프링\"]}";

        ClassificationResult result = service.parseResult(json);

        assertThat(result.category()).isEqualTo(Category.DEVELOPMENT);
        assertThat(result.tags()).containsExactly("스프링", "JPA"); // 중복 제거 + 첫 등장 순서 유지
    }

    @Test
    void parseResult_정상응답은_그대로_파싱한다() throws Exception {
        String json = "{\"category\":\"CAREER\",\"tags\":[\"이력서\",\"면접\"]}";

        ClassificationResult result = service.parseResult(json);

        assertThat(result.category()).isEqualTo(Category.CAREER);
        assertThat(result.tags()).containsExactly("이력서", "면접");
        assertThat(result.title()).isNull(); // 텍스트 분류 응답엔 title이 없어 null
    }

    @Test
    void parseResult_비전응답의_title을_캡션으로_파싱한다() throws Exception { // 설계결정 #32
        String json = "{\"title\":\"스프링 필터 체인 다이어그램\",\"category\":\"DEVELOPMENT\",\"tags\":[\"스프링\"]}";

        ClassificationResult result = service.parseResult(json);

        assertThat(result.title()).isEqualTo("스프링 필터 체인 다이어그램");
        assertThat(result.category()).isEqualTo(Category.DEVELOPMENT);
        assertThat(result.tags()).containsExactly("스프링");
    }

    @Test
    void parseResult_빈_title은_null로_둔다() throws Exception { // 공백 캡션이면 호출자가 날짜형 폴백을 쓰게
        String json = "{\"title\":\"  \",\"category\":\"ETC\",\"tags\":[]}";

        ClassificationResult result = service.parseResult(json);

        assertThat(result.title()).isNull();
    }
}
