package com.curio.processor;

import com.curio.entity.enums.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * detectType 분기 로직 단위 테스트.
 * detectType은 주입 의존성을 쓰지 않는 순수 분기라, 의존성은 null로 두고 메서드만 검증한다.
 */
class ItemProcessorTest {

    private final ItemProcessor processor = new ItemProcessor(null, null, null, null, null);

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource({
            "'그냥 메모임',                  TEXT",   // URL 없음
            "'https://youtu.be/abc',          LINK",   // 평범한 링크
            "'https://x.com/a.png',           IMAGE",  // 이미지 확장자
            "'https://x.com/a.PNG',           IMAGE",  // 확장자 대소문자 무시
            "'https://x.com/a.png?w=200',     IMAGE",  // 확장자 뒤 쿼리스트링
            "'사진봐 https://x.com/a.jpg',    IMAGE",  // 텍스트에 URL 섞임 (첫 URL 추출)
            "'www.naver.com',                 TEXT",   // 스킴(http) 없으면 링크로 안 봄
            "'https://x.com/a.png/more',      LINK"    // 확장자가 경로 끝이 아님
    })
    void detectType_입력에따라_타입을_분류한다(String input, ItemType expected) {
        assertThat(processor.detectType(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource({
            "'https://Example.com/Path',            'https://example.com/Path'",            // host만 소문자, path 대소문자 유지
            "'https://example.com/path/',           'https://example.com/path'",            // 끝 슬래시 제거
            "'http://example.com/a',                'https://example.com/a'",               // http -> https 통일
            "'https://example.com/a?utm_source=nl', 'https://example.com/a'",               // utm_ 추적 파라미터 제거
            "'https://example.com/search?q=hi',     'https://example.com/search?q=hi'",     // 의미 있는 쿼리는 보존
            "'https://example.com',                 'https://example.com'"                  // path 없음
    })
    void normalizeUrl_중복판정용으로_정규화한다(String input, String expected) {
        assertThat(processor.normalizeUrl(input)).isEqualTo(expected);
    }

    /**
     * 회귀 방어: 쿼리를 통째로 버리던 옛 동작은 서로 다른 유튜브 영상을 중복 판정했다.
     * 이제 v 파라미터를 보존하므로 다른 영상은 다른 URL이 된다.
     */
    @Test
    void normalizeUrl_유튜브_다른영상은_다른URL로_보존된다() {
        String a = processor.normalizeUrl("https://www.youtube.com/watch?v=AAA");
        String b = processor.normalizeUrl("https://www.youtube.com/watch?v=BBB");

        assertThat(a).isEqualTo("https://www.youtube.com/watch?v=AAA"); // v 보존
        assertThat(a).isNotEqualTo(b);                                  // 다른 영상 -> 다른 URL
    }

    @Test
    void normalizeUrl_의미있는쿼리는_두고_추적파라미터만_제거한다() {
        String r = processor.normalizeUrl("https://www.youtube.com/watch?v=AAA&utm_source=share");

        assertThat(r).isEqualTo("https://www.youtube.com/watch?v=AAA"); // v 유지, utm_ 제거
    }

    @ParameterizedTest(name = "[{index}] 추적 파라미터 제거: {0}")
    @CsvSource({
            "'https://example.com/p?id=1&utm_source=x', 'https://example.com/p?id=1'",  // utm_ 접두
            "'https://example.com/p?id=1&fbclid=x',     'https://example.com/p?id=1'",  // 페이스북
            "'https://example.com/p?id=1&gclid=x',      'https://example.com/p?id=1'",  // 구글 Ads
            "'https://example.com/p?id=1&msclkid=x',    'https://example.com/p?id=1'",  // MS/Bing
            "'https://example.com/p?id=1&_hsenc=x',     'https://example.com/p?id=1'"   // HubSpot
    })
    void normalizeUrl_추적파라미터는_제거하고_나머지는_보존한다(String input, String expected) {
        assertThat(processor.normalizeUrl(input)).isEqualTo(expected);
    }

    // --- textTitle: TEXT 제목 = 첫 줄 (설계결정 #35) ---

    @Test
    void textTitle_첫_줄을_제목으로_쓴다() {
        assertThat(processor.textTitle("첫 줄\n둘째 줄\n셋째 줄")).isEqualTo("첫 줄");
    }

    @Test
    void textTitle_앞뒤_공백을_정리한다() {
        assertThat(processor.textTitle("  \n  진짜 제목  \n뒤")).isEqualTo("진짜 제목");
    }

    @Test
    void textTitle_줄바꿈_없으면_전체가_제목() {
        assertThat(processor.textTitle("짧은 한 줄 메모")).isEqualTo("짧은 한 줄 메모");
    }

    @Test
    void textTitle_100자_넘는_첫줄은_잘라_말줄임() {
        String t = processor.textTitle("가".repeat(150));
        assertThat(t).hasSize(101).endsWith("…"); // 100자 + …
    }
}
