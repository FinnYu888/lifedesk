package com.lifedesk.auth;

import com.lifedesk.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 认证服务：登录 / 注册。
 *
 * <p>sys_user 表由 DatabaseMigration 负责补齐 username/password 列并预置账号
 * （用户名：俞鹏飞，密码：finn，id=1，历史数据全部归属该账号）。
 * 这里走 JdbcTemplate 直查，绕开多租户插件（sys_user 已在忽略清单，双保险）。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final TokenStore tokenStore;

    /** 登录：校验用户名密码，签发 token */
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new BizException("请输入用户名和密码");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, nickname FROM sys_user WHERE username = ? AND password = ? AND deleted = 0",
                username.trim(), password);
        if (rows.isEmpty()) {
            throw new BizException("用户名或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        long userId = ((Number) user.get("id")).longValue();
        String token = tokenStore.issue(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("token", token);
        result.put("userId", userId);
        result.put("nickname", String.valueOf(user.get("nickname")));
        return result;
    }

    /** 注册：用户名唯一，创建后直接登录 */
    public Map<String, Object> register(String username, String password) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new BizException("请输入用户名和密码");
        }
        String name = username.trim();
        if (name.length() > 64) {
            throw new BizException("用户名过长（最多 64 字符）");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0",
                Integer.class, name);
        if (exists != null && exists > 0) {
            throw new BizException("该用户名已被注册");
        }
        jdbcTemplate.update(
                "INSERT INTO sys_user(nickname, username, password) VALUES (?, ?, ?)",
                name, name, password);
        return login(name, password);
    }
}
