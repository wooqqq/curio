package com.curio.common;

import com.curio.exception.CurioException;
import com.curio.exception.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 방지 — 외부에서 받은 URL을 서버가 fetch하기 전, 내부/사설 대역으로 향하지 않는지 검증한다.
 * 크롤(OgCrawlerService)과 이미지 다운로드(S3Service)의 공통 길목. 설계결정 #27.
 *
 * 한계: 호스트를 지금 해석한 IP로 판단하므로, 검증 후 실제 fetch 시 DNS가 내부 IP로 바뀌는
 * rebinding은 못 막는다. 매 리다이렉트 홉을 재검증해 흔한 우회는 닫되, IP 핀잉까지는 안 한다
 * (인증 사용자·소규모 트래픽 기준 트레이드오프).
 */
public final class UrlGuard {

    private UrlGuard() {
    }

    /**
     * http/https 공개 주소인지 확인한다. 스킴이 다르거나 호스트가 루프백/사설/링크로컬/메타데이터
     * 대역으로 해석되면 fetch 전에 CurioException(BLOCKED_URL)을 던진다.
     */
    public static void verifyPublic(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new CurioException(ErrorCode.BLOCKED_URL, "올바르지 않은 주소입니다.");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new CurioException(ErrorCode.BLOCKED_URL, "http/https 주소만 허용됩니다.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CurioException(ErrorCode.BLOCKED_URL, "주소의 호스트를 확인할 수 없습니다.");
        }
        // IPv6 리터럴은 URI.getHost()가 대괄호를 포함해 줄 수 있다([::1]) → 해석 전에 벗긴다.
        // 안 벗기면 getByName이 실패해 공개 IPv6 주소까지 막아버린다(과차단).
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // 해석되지 않는 호스트는 fetch하지 않는다(안전 측).
            throw new CurioException(ErrorCode.BLOCKED_URL, "주소를 해석할 수 없습니다.");
        }
        // 한 호스트가 여러 IP로 풀릴 수 있으니 하나라도 막힌 대역이면 거절한다.
        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new CurioException(ErrorCode.BLOCKED_URL);
            }
        }
    }

    private static boolean isBlocked(InetAddress addr) {
        if (addr.isAnyLocalAddress()       // 0.0.0.0, ::
                || addr.isLoopbackAddress()    // 127.0.0.0/8, ::1
                || addr.isLinkLocalAddress()   // 169.254.0.0/16(메타데이터 169.254.169.254 포함), fe80::/10
                || addr.isSiteLocalAddress()   // 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
            // 100.64.0.0/10 (CGNAT) — 일부 클라우드 내부망에 쓰여 위 메서드가 못 잡는다.
            return b0 == 100 && b1 >= 64 && b1 <= 127;
        }
        if (b.length == 16) {
            // IPv6 unique local fc00::/7 (fc·fd로 시작)
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
