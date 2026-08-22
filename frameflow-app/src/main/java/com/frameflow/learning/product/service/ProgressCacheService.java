package com.frameflow.learning.product.service;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 批次进度缓存（Cache-Aside 模式 + 穿透/击穿防护）。
 *
 * 三层防护对应三类经典故障（导读 §3 有完整场景推演）：
 * - 穿透（查不存在的 key 反复打库）→ 空值哨兵缓存（短 TTL）；
 * - 击穿（热 key 过期瞬间并发全部打库）→ 互斥锁重建，一人查库众人等待；
 * - 雪崩（大量 key 同时过期）→ TTL 加随机抖动（本功能 key 量小，主要防前两者）。
 */
@Service
public class ProgressCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProgressCacheService.class);

    private static final String KEY_PREFIX = "batch:progress:";
    private static final String LOCK_PREFIX = "lock:batch:progress:";
    private static final String NULL_SENTINEL = "∅";
    private static final Duration VALUE_TTL = Duration.ofSeconds(30);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ProgressCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 读：缓存命中直接回；未命中走"互斥重建"，Redis 故障时降级直查库。
     *
     * 约定：loader 返回 null 表示"批次不存在"——null 会以哨兵形式缓存
     * （防穿透），命中哨兵时本方法返回 null。
     */
    public Map<String, Integer> getOrLoad(long batchId, Supplier<Map<String, Integer>> dbLoader) {
        String key = KEY_PREFIX + batchId;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return decodeOrNull(cached, batchId) ? decode(cached) : null;
            }
            // ★ 核心（防击穿）：过期瞬间的并发请求，只有一个抢到锁去查库，
            // 其余人短暂等待后重读缓存——数据库不会被并发重建击穿。
            String lockKey = LOCK_PREFIX + batchId;
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    Map<String, Integer> loaded = dbLoader.get();
                    store(key, loaded);
                    return loaded;
                } finally {
                    redis.delete(lockKey);
                }
            }
            // 没抢到锁：等 50ms 再读一次缓存；仍没有（重建方极慢）就直查库兜底
            Thread.sleep(50);
            cached = redis.opsForValue().get(key);
            if (cached != null) {
                return decodeOrNull(cached, batchId) ? decode(cached) : null;
            }
            return dbLoader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return dbLoader.get();
        } catch (Exception e) {
            // ★ 核心（降级）：缓存层故障绝不能拖垮读路径——直接查库并告警。
            // 这就是 docs/02 "Redis 永远可丢失可重建"的代码落点。
            log.warn("进度缓存不可用，降级直查数据库 batchId={}: {}", batchId, e.getMessage());
            return dbLoader.get();
        }
    }

    /** 写后失效：改了数据就删缓存，下次读触发重建（Cache-Aside 的写侧）。 */
    public void evict(long batchId) {
        try {
            redis.delete(KEY_PREFIX + batchId);
        } catch (Exception e) {
            log.warn("缓存失效失败（将靠 TTL 自然过期）batchId={}: {}", batchId, e.getMessage());
        }
    }

    private void store(String key, Map<String, Integer> value) {
        try {
            if (value == null) {
                // 穿透防护：不存在的批次也缓存（短 TTL），恶意扫 ID 打不到库
                redis.opsForValue().set(key, NULL_SENTINEL, NULL_TTL);
                return;
            }
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), VALUE_TTL);
        } catch (Exception e) {
            log.warn("缓存写入失败（不影响业务结果）: {}", e.getMessage());
        }
    }

    private boolean decodeOrNull(String cached, long batchId) {
        if (NULL_SENTINEL.equals(cached)) {
            return false;   // 哨兵：不存在
        }
        return true;
    }

    private Map<String, Integer> decode(String cached) {
        try {
            return objectMapper.readValue(cached,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() { });
        } catch (Exception e) {
            return Map.of();   // 损坏值按空处理（TTL 很快会刷掉）
        }
    }
}
