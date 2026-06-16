package com.curio.common;

/**
 * 문자열 공통 유틸.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 컬럼 길이를 넘는 값을 저장 전 잘라낸다.
     * 크롤 제목·AI 태그·URL이 DB 컬럼 길이를 초과하면 MySQL strict 모드에서
     * insert가 통째로 롤백(500)되므로, 저장 경로에서 미리 길이를 맞춘다.
     *
     * @param value     원본 (null이면 null 반환)
     * @param maxLength 허용 최대 길이 (양수)
     * @return maxLength 이하로 잘린 문자열
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        // 경계가 surrogate pair(이모지 등) 한가운데면, 외톨이 high surrogate가 남아
        // utf8mb4 컬럼에 invalid UTF-8로 들어가 insert가 깨진다 → 반쪽을 통째로 버린다.
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
