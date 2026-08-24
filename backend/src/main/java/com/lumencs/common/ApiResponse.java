package com.lumencs.common;

/**
 * 兼容旧调用点：ok / error 实际返回脚手架风格 {@link R}。
 */
public final class ApiResponse {
    private ApiResponse() {}

    public static <T> R<T> ok(T data) {
        return R.success(data);
    }

    public static <T> R<T> error(int code, String message) {
        return R.fail(code, message);
    }
}
