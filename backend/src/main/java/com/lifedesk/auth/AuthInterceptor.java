package com.lifedesk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;

/**
 * 认证拦截器：除 /auth/** 与 OPTIONS 预检外，所有请求必须携带有效令牌。
 *
 * <p>令牌格式：请求头 Authorization: Bearer &lt;token&gt;。
 * 校验通过后把 userId 写入 {@link UserContext}，供多租户插件做数据隔离；
 * 未认证返回 401 JSON（前端据此跳转登录页）。</p>
 *
 * @author LifeDesk
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        Long userId = tokenStore.resolve(token);
        // 兜底：<audio>/<video> 等媒体元素无法携带 Authorization 头，
        // 允许 GET 请求用 ?token=xxx 查询参数鉴权（音乐文件流式播放场景）
        if (userId == null && "GET".equalsIgnoreCase(request.getMethod())) {
            userId = tokenStore.resolve(request.getParameter("token"));
        }
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("code", 401);
            body.put("msg", "未登录或登录已过期");
            body.put("data", null);
            response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
            response.getOutputStream().flush();
            return false;
        }
        UserContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 线程池复用，必须清理，防止用户串号
        UserContext.clear();
    }
}
