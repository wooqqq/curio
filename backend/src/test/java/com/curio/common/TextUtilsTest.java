package com.curio.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * truncate 단위 테스트.
 * 크롤 제목·AI 태그·URL이 컬럼 길이를 넘을 때 저장 전 잘라 strict 모드 insert 롤백(500)을 막는다(예측 버그 #2).
 */
class TextUtilsTest {

    @Test
    void null이면_null을_반환한다() {
        assertThat(TextUtils.truncate(null, 10)).isNull();
    }

    @Test
    void 길이가_같거나_짧으면_그대로_둔다() {
        assertThat(TextUtils.truncate("abcde", 5)).isEqualTo("abcde");
        assertThat(TextUtils.truncate("abc", 5)).isEqualTo("abc");
        assertThat(TextUtils.truncate("", 5)).isEqualTo("");
    }

    @Test
    void 길이를_넘으면_최대길이로_자른다() {
        assertThat(TextUtils.truncate("abcdef", 3)).isEqualTo("abc");

        String longTitle = "x".repeat(700);
        assertThat(TextUtils.truncate(longTitle, 500)).hasSize(500);
    }

    @Test
    void 경계가_이모지_한가운데면_반쪽_surrogate를_남기지않는다() {
        // 😀 = U+1F600 = surrogate pair(자바 length 2). "a😀"를 2에서 자르면 외톨이 high surrogate가 남는다.
        String result = TextUtils.truncate("a😀", 2);

        assertThat(result).isEqualTo("a"); // 깨진 반쪽 대신 통째로 버림
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 1))).isFalse();
        assertThat(result.codePoints().allMatch(Character::isValidCodePoint)).isTrue();
    }

    @Test
    void 경계가_이모지_뒤쪽이면_온전한_이모지를_보존한다() {
        // "a😀"를 3에서 자르면 변화 없음(전체 길이 3 == max).
        assertThat(TextUtils.truncate("a😀", 3)).isEqualTo("a😀");
    }
}
