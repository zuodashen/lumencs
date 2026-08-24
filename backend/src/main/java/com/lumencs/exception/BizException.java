package com.lumencs.exception;

public class BizException extends RuntimeException {
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ExceptionCode exceptionCode) {
        super(exceptionCode.getMessage());
        this.code = exceptionCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
