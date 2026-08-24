package com.lumencs.common;

public final class TraceContext {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private TraceContext() {}

    public static void setTraceId(String traceId) {
        TRACE.set(traceId);
    }

    public static String getTraceId() {
        return TRACE.get();
    }

    public static void clear() {
        TRACE.remove();
    }
}
