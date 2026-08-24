package com.lumencs.common;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class R<T> {
    public static final int SUCCESS_CODE = 200;

    private int state;
    private T data;
    private String msg;
    private String path;
    private String traceId;
    private long timestamp = System.currentTimeMillis();

    public static <E> R<E> success(E data) {
        return new R<E>().setState(SUCCESS_CODE).setData(data).setMsg("SUCCESS").setTraceId(TraceContext.getTraceId());
    }

    public static <E> R<E> success(E data, String msg) {
        return success(data).setMsg(msg);
    }

    public static <E> R<E> fail(int state, String msg) {
        return new R<E>().setState(state).setMsg(msg).setTraceId(TraceContext.getTraceId());
    }

    public Boolean getIsSuccess() {
        return state == SUCCESS_CODE;
    }
}
