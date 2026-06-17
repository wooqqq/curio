package com.curio.service;

import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * titleFromUrl 단위 테스트. 네트워크를 타지 않는 순수 문자열 로직이라 의존성 없이 검증한다.
 */
class OgCrawlerServiceTest {

    private final OgCrawlerService crawler = new OgCrawlerService();

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource({
            "'https://example.com/how-to-test-code',       'how to test code'",   // 기본 슬러그 (하이픈 -> 공백)
            "'https://medium.com/some-post-b34ee4cc2bc2',  'some post'",           // Medium 끝 해시 id 제거
            "'https://example.com/my-article/',            'my article'",          // 끝 슬래시 -> 마지막 세그먼트
            "'https://example.com/page.html',              'page'",                // 확장자 제거
            "'https://www.naver.com',                      'naver.com'",           // path 없음 -> host (www 제거)
            "'https://example.com/12345',                  'example.com'",         // 숫자만 -> host 폴백
            "'https://example.com/a',                      'example.com'",         // 너무 짧음 -> host 폴백
            "'https://example.com/%ED%85%8C%EC%8A%A4%ED%8A%B8-%EA%B8%80', '테스트 글'"  // URL 디코딩
    })
    void titleFromUrl_슬러그에서_제목을_복원한다(String url, String expected) {
        assertThat(crawler.titleFromUrl(url)).isEqualTo(expected);
    }

    /**
     * ★ 알려진 약점(특성 테스트): 유튜브 watch URL은 슬러그가 'watch'뿐이라
     * titleFromUrl만으로는 제목이 'watch'로 깨진다. 그래서 crawl()은 유튜브를
     * oEmbed로 먼저 처리한다(설계결정 #18). 이 테스트는 그 약점을 명시·고정한다.
     */
    @Test
    void titleFromUrl_유튜브watch는_watch로_깨진다_oEmbed로_보완() {
        assertThat(crawler.titleFromUrl("https://www.youtube.com/watch")).isEqualTo("watch");
    }

    /**
     * SSRF 방지: crawl()은 fetch 전에 UrlGuard로 내부/비http 주소를 막는다(설계결정 #27).
     * 검증이 네트워크 호출 전에 BLOCKED_URL을 던지므로 외부 통신 없이 확인된다.
     */
    @ParameterizedTest(name = "[{index}] {0} 차단")
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "http://127.0.0.1/",
            "http://192.168.0.1/",
            "ftp://example.com/x"
    })
    void crawl_내부_또는_비http_주소는_BLOCKED_URL로_막는다(String url) {
        assertThatThrownBy(() -> crawler.crawl(url))
                .isInstanceOf(CurioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BLOCKED_URL);
    }
}
