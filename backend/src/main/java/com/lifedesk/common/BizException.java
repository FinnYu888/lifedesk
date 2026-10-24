package com.lifedesk.common;

/**
 * 业务异常。
 *
 * <p>Service 层抛出该异常会被 GlobalExceptionHandler 捕获并转为统一响应。</p>
 *
 * @author LifeDesk
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}