package com.lifedesk.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>所有 Controller 返回该对象，前端按 code 判定成功失败。</p>
 *
 * @param <T> 业务数据类型
 * @author LifeDesk
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功，非 0 失败 */
    private Integer code;

    /** 提示消息 */
    private String msg;

    /** 业务数据 */
    private T data;

    public R() {
    }

    public R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(0, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(0, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }

    public static <T> R<T> fail(Integer code, String msg) {
        return new R<>(code, msg, null);
    }
}