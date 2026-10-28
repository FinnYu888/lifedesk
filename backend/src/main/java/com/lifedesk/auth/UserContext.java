package com.lifedesk.auth;

/**
 * 当前登录用户上下文（请求线程级）。
 *
 * <p>AuthInterceptor 校验 token 后写入 userId，业务层 / 多租户插件从这里读取；
 * 异步线程（git 同步、OSS 统计等）无线程上下文，租户插件会回退到默认用户 1。</p>
 *
 * @author LifeDesk
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /** @return 当前用户 id；未登录（异步线程等）返回 null */
    public static Long get() {
        return USER_ID.get();
    }

    /** @return 当前用户 id；未登录回退默认用户 1（俞鹏飞） */
    public static long getOrDefault() {
        Long id = USER_ID.get();
        return id != null ? id : 1L;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
