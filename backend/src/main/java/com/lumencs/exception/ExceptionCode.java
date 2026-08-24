package com.lumencs.exception;

/**
 * 业务异常码：与全局异常处理器配合，向前端返回统一 R{state,msg}。
 */
public enum ExceptionCode {

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突或非法流转"),
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ExceptionCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
