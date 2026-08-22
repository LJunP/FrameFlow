package com.frameflow.learning.shared.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 令牌桶限流（Redis + Lua 原子实现）。
 *
 * ★ 核心：为什么必须 Lua——"读余量→计算→扣减"是三步操作，分开执行时
 * 两个并发请求会同时读到相同余量并双双通过（竞态）。Lua 脚本在 Redis
 * 单线程里原子执行，天然免锁。
 *
 * 令牌桶 vs 固定窗口：固定窗口有"边界突刺"（窗口切换瞬间可通过 2 倍额度）；
 * 令牌桶按速率持续回填，突发额度受容量硬约束，平滑得多。
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final DefaultRedisScript<Long> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local last = tonumber(state[2])
            if tokens == nil then
                tokens = tonumber(ARGV[1])
                last = tonumber(ARGV[3])
            end
            local elapsedSec = math.max(0, tonumber(ARGV[3]) - last) / 1000.0
            tokens = math.min(tonumber(ARGV[1]), tokens + elapsedSec * tonumber(ARGV[2]))
            local allowed = 0
            if tokens >= tonumber(ARGV[4]) then
                tokens = tokens - tonumber(ARGV[4])
                allowed = 1
            end
            redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', tonumber(ARGV[3]))
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
            return allowed
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param key          限流维度键（如 "ratelimit:login:user@x.com"）
     * @param capacity     桶容量（突发上限）
     * @param refillPerSec 每秒回填令牌数
     * @return true=放行
     */
    public boolean tryAcquire(String key, int capacity, double refillPerSec) {
        try {
            Long allowed = redis.execute(TOKEN_BUCKET, List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSec),
                    String.valueOf(System.currentTimeMillis()),
                    "1",
                    "3600");   // 桶 1 小时不用就过期，防 key 堆积
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            // ★ 核心：fail-open（降级放行）——限流是"保护层"不是"依赖层"，
            // Redis 挂了应该放行并告警，而不是让登录/下单整体瘫痪。
            // 代价是故障窗口内限流失效，这个取舍在导读 §3 详述。
            log.warn("限流器不可用，降级放行: {}", e.getMessage());
            return true;
        }
    }
}
