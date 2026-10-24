package com.lifedesk.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>按异常类型分别返回 4xx / 5xx，避免把客户端错误（参数缺失、JSON 解析失败、方法不支持）
 * 一律误判为"系统异常 500"——这是前端"大量接口 500 错误"的根因。</p>
 *
 * <p>异常 → code 映射：</p>
 * <ul>
 *   <li>{@link BizException}          → 用 e.getCode()（业务自定义）</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} → 400 参数校验失败</li>
 *   <li>{@link MissingServletRequestParameterException} → 400 缺少必填参数</li>
 *   <li>{@link MethodArgumentTypeMismatchException}    → 400 参数类型转换失败</li>
 *   <li>{@link HttpMessageNotReadableException}        → 400 请求体不可读 / JSON 格式错误</li>
 *   <li>{@link HttpRequestMethodNotSupportedException} → 405 请求方法不支持</li>
 *   <li>{@link NoHandlerFoundException}               → 404 接口不存在</li>
 *   <li>其他 {@link Exception}                         → 500 系统异常（兜底）</li>
 * </ul>
 *
 * @author LifeDesk
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(400, "参数校验失败: " + msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return R.fail(400, "参数绑定失败: " + msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return R.fail(400, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型转换失败: {}={}", e.getName(), e.getValue());
        return R.fail(400, "参数类型错误: " + e.getName() + " 期望 " + e.getRequiredType());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        log.warn("请求体不可读: {} {}, msg={}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return R.fail(400, "请求体格式错误（缺少 Body 或 JSON 解析失败）");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        log.warn("请求方法不支持: {} {} (supported={})", req.getMethod(), req.getRequestURI(), e.getSupportedHttpMethods());
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandler(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return R.fail(404, "接口不存在: " + e.getRequestURL());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e, HttpServletRequest req) {
        log.error("系统异常: {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage(), e);
        return R.fail(500, "系统异常: " + e.getMessage());
    }
}
