package com.curio.exception;

import lombok.Getter;

@Getter
public class CurioException extends RuntimeException {

    private final ErrorCode errorCode;

    public CurioException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CurioException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
