package com.lifedesk.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌存储：token → userId。
 *
 * <p>个人工具场景使用进程内令牌（重启后需重新登录，见谅）；
 * 若后续需要集群/持久化，替换为实现同一接口的 Redis 版本即可。</p>
 *
 * @author LifeDesk
 */
@Component
public class TokenStore {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    /** 签发新令牌 */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, userId);
        return token;
    }

    /** 校验令牌，无效返回 null */
    public Long resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return tokens.get(token);
    }

    /** 注销 */
    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
