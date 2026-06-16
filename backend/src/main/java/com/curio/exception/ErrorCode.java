package com.curio.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "리프레시 토큰을 찾을 수 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // Item
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "아이템을 찾을 수 없습니다."),
    DUPLICATE_URL(HttpStatus.CONFLICT, "DUPLICATE_URL", "이미 저장된 링크입니다."),
    RECRAWL_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "RECRAWL_NOT_SUPPORTED", "링크 타입 아이템만 다시 불러올 수 있습니다."),
    BLOCKED_URL(HttpStatus.BAD_REQUEST, "BLOCKED_URL", "허용되지 않는 주소입니다."),

    // Kakao Bot
    BOT_USER_NOT_LINKED(HttpStatus.FORBIDDEN, "BOT_USER_NOT_LINKED", "카카오 봇 계정 연동이 필요합니다."),
    INVALID_LINK_CODE(HttpStatus.BAD_REQUEST, "INVALID_LINK_CODE", "유효하지 않거나 만료된 연동 코드입니다."),

    // Announcement / Popup
    ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND", "공지를 찾을 수 없습니다."),
    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "POPUP_NOT_FOUND", "팝업을 찾을 수 없습니다."),

    // Upload
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "이미지 파일이 올바르지 않습니다."),
    S3_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "S3_NOT_CONFIGURED", "이미지 저장소(S3)가 설정되지 않았습니다."),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "이미지 업로드에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
