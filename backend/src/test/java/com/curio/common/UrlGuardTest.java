package com.curio.common;

import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF 가드(UrlGuard.verifyPublic) 단위 테스트.
 * IP 리터럴·localhost로 검증해 네트워크(외부 DNS)를 타지 않는다.
 */
class UrlGuardTest {

    @ParameterizedTest(name = "[{index}] {0} 차단")
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/", // 클라우드 메타데이터(링크로컬)
            "http://127.0.0.1/",                        // 루프백
            "http://localhost/admin",                   // 루프백 호스트명
            "http://10.0.0.1/",                         // 사설 10/8
            "http://172.16.0.5/",                       // 사설 172.16/12
            "http://192.168.0.1/",                      // 사설 192.168/16
            "http://100.64.0.1/",                       // CGNAT 100.64/10
            "http://[::1]/",                            // IPv6 루프백
            "http://0.0.0.0/",                          // any-local
            "ftp://example.com/x",                      // http/https 아님
            "file:///etc/passwd",                       // 스킴 차단
            "not-a-url"                                 // 파싱 불가
    })
    void 내부_또는_비http_주소는_BLOCKED_URL로_막는다(String url) {
        assertThatThrownBy(() -> UrlGuard.verifyPublic(url))
                .isInstanceOf(CurioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BLOCKED_URL);
    }

    @ParameterizedTest(name = "[{index}] {0} 허용")
    @ValueSource(strings = {
            "http://8.8.8.8/",                  // 공개 IPv4 리터럴(DNS 불필요)
            "https://1.1.1.1/path?q=1",         // 공개 IPv4 + 경로·쿼리
            "https://[2606:4700:4700::1111]/"   // 공개 IPv6 리터럴(대괄호 벗겨 해석 — 과차단 안 됨)
    })
    void 공개_http_주소는_통과한다(String url) {
        assertThatNoException().isThrownBy(() -> UrlGuard.verifyPublic(url));
    }
}
