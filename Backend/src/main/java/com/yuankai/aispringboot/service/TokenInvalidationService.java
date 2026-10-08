package com.yuankai.aispringboot.service;

import com.yuankai.aispringboot.consts.RedisKeyConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 「改密即踢下线」支持：按用户记录最近一次密码变更时间，
 * 让变更之前签发的 JWT 立即失效。
 *
 * <p>时间精度：JWT 的 iat 是秒级时间戳，所以水位线也存秒级值，并用「严格小于」比较 ——
 * 同一秒内签发的 token 视为有效，避免改密后立刻重新登录拿到的新 token 被误杀。</p>
 *
 * <p>fail-open：Redis 不可用时一律放行。判断不出 token 新鲜度只是回退到改造前的行为
 * （旧 token 还能用一会儿），而让鉴权整体失败会直接导致全站不可用，代价不对等。</p>
 */
@Service
public class TokenInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(TokenInvalidationService.class);

    private final StringRedisTemplate redisTemplate;

    public TokenInvalidationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录一次密码变更，把该用户的时间水位线推到当前时刻。
     *
     * @param userId    用户 id
     * @param ttlMillis 水位线存活时长，取 token 最长有效期即可（到期后旧 token 本身也已过期，无需再判）
     */
    public void markPasswordChanged(Long userId, long ttlMillis) {
        if (userId == null || ttlMillis <= 0) {
            return;
        }
        String key = RedisKeyConsts.PASSWORD_CHANGED_PREFIX + userId;
        long changedAtSeconds = System.currentTimeMillis() / 1000;
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(changedAtSeconds), ttlMillis, TimeUnit.MILLISECONDS);
            log.info("用户 {} 的密码变更水位线已刷新为 {}（秒）", userId, changedAtSeconds);
        } catch (Exception e) {
            log.warn("写入密码变更水位线失败（Redis 不可用？），fail-open：该用户改密前签发的 token 在 Redis 恢复前仍可用", e);
        }
    }

    /**
     * 判断 token 是否早于该用户最近一次密码变更。
     *
     * @param userId          用户 id
     * @param issuedAtSeconds token 的签发时间（秒，来自 JWT 的 iat）
     * @return true 表示该 token 在改密前签发，应当拒绝
     */
    public boolean isIssuedBeforePasswordChange(Long userId, Long issuedAtSeconds) {
        // 缺少签发时间（老格式 token / 解析异常）时不做判断：宁可放过，也不误杀正常会话
        if (userId == null || issuedAtSeconds == null) {
            return false;
        }
        String key = RedisKeyConsts.PASSWORD_CHANGED_PREFIX + userId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return false;
            }
            long changedAtSeconds = Long.parseLong(value.trim());
            return issuedAtSeconds < changedAtSeconds;
        } catch (NumberFormatException e) {
            log.warn("密码变更水位线值异常（{}），跳过本次校验", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("密码变更校验失败（Redis 不可用？），fail-open 放行 token", e);
            return false;
        }
    }
}
