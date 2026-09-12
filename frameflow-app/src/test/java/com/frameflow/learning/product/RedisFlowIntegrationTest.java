package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.frameflow.learning.TestcontainersConfiguration;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.shared.ratelimit.RedisRateLimiter;
import com.frameflow.learning.storage.MinioTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F5 集成测试：真实 Redis 容器上的限流与缓存正反例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MinioTestConfig.class})
class RedisFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StoragePort storage;

    // ---------- T1 登录限流 ----------

    @Test
    void login_rate_limit_kicks_in_after_burst() throws Exception {
        String email = "burst@example.com";
        register(email);
        String body = "{\"email\":\"" + email + "\",\"password\":\"WrongPass!\"}";

        // 容量 5：前 5 次是正常的 401（密码错误）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        // 第 6 次：桶空了 → 429（限流先于密码校验，连"邮箱是否存在"都不泄露）
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // 桶的 key 带过期时间（1 小时无人使用自动清理）
        assertThat(redis.getExpire("ratelimit:login:" + email)).isPositive();
    }

    @Test
    void rate_limit_is_per_key_not_global() throws Exception {
        // 限流按邮箱分桶：A 被限流不影响 B 正常登录
        String a = "limited@example.com";
        String b = "normal@example.com";
        register(a);
        register(b);
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + a + "\",\"password\":\"WrongPass!\"}"));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + b + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ---------- T2/T3 进度缓存 ----------

    @Test
    void progress_cache_read_through_and_invalidation() throws Exception {
        var ctx = preparedBatch("cache@example.com");

        // 第一次读（缓存优先端点）：未命中 → 查库回填，key 带 TTL
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/progress")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("batch:progress:" + ctx.batchId)).isTrue();
        assertThat(redis.getExpire("batch:progress:" + ctx.batchId)).isPositive();

        // 上传完成 → 写后失效 → 再读立即反映新状态（不是等 30s TTL）
        completeUpload(ctx);
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/progress")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.UPLOADED").value(1));
    }

    @Test
    void warm_progress_cache_does_not_bypass_team_authorization() throws Exception {
        var ctx = preparedBatch("private-progress@example.com");
        var outsider = registerAndGetTokens("outsider-progress@example.com");
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/progress")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("batch:progress:" + ctx.batchId)).isTrue();
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/progress")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        // 缓存清空后的路径也必须得到同一个拒绝结果。
        redis.delete("batch:progress:" + ctx.batchId);
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/progress")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void penetration_guard_caches_missing_batch_sentinel() throws Exception {
        var tokens = registerAndGetTokens("pen@example.com");
        long ghostId = 987654321L;

        // 不存在的批次仍保留空响应与哨兵契约；归属查询不由共享缓存替代。
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/batches/" + ghostId + "/progress")
                            .header("Authorization", bearer(tokens)))
                    .andExpect(status().isOk());
        }
        assertThat(redis.hasKey("batch:progress:" + ghostId)).isTrue();
        assertThat(redis.opsForValue().get("batch:progress:" + ghostId)).isEqualTo("∅");
    }

    // ---------- T4 降级（Redis 故障 = 放行/直查库，不瘫痪） ----------

    @Test
    void rate_limiter_fails_open_when_redis_down() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(), any(java.util.List.class),
                any(Object[].class))).thenThrow(new RuntimeException("connection refused"));
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var metrics = new com.frameflow.learning.observability.FrameFlowMetrics(registry);
        RedisRateLimiter limiter = new RedisRateLimiter(broken, metrics);

        // Redis 挂了：限流器放行（保护失效优于业务瘫痪——取舍见导读）
        assertThat(limiter.tryAcquire("any", 5, 1.0)).isTrue();
        assertThat(registry.get("frameflow.dependency.degradation")
                .tags("dependency", "redis", "operation", "rate_limit")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void progress_cache_falls_back_to_db_when_redis_down() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var metrics = new com.frameflow.learning.observability.FrameFlowMetrics(registry);
        com.frameflow.learning.product.service.ProgressCacheService cache =
                new com.frameflow.learning.product.service.ProgressCacheService(
                        broken, objectMapper, metrics);

        Map<String, Integer> loaded = cache.getOrLoad(42L, () -> Map.of("UPLOADED", 7));
        assertThat(loaded).containsEntry("UPLOADED", 7);   // 直查库兜底成功
        assertThat(registry.get("frameflow.dependency.degradation")
                .tags("dependency", "redis", "operation", "cache_read")
                .counter().count()).isEqualTo(1);
    }

    // ---------- 工具 ----------

    private record Ctx(Map<String, Object> tokens, long batchId) { }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"displayName\":\"x\"}"))
                .andExpect(status().isOk());
    }

    private Map<String, Object> registerAndGetTokens(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"displayName\":\"x\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private Ctx preparedBatch(String email) throws Exception {
        Map<String, Object> tokens = registerAndGetTokens(email);
        String auth = bearer(tokens);
        long projectId = idOf(mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"p-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"b\"}")).andExpect(status().isCreated());
        long profileId = idOf(mockMvc.perform(post("/api/v1/quality-profiles")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "s" + UUID.randomUUID()
                        .toString().substring(0, 6), "spec", "{\"dimensions\":{}}"))))
                .andExpect(status().isCreated()).andReturn());
        long batchId = idOf(mockMvc.perform(post("/api/v1/batches")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectId", projectId, "profileId", profileId, "capacity", 3))))
                .andExpect(status().isCreated()).andReturn());
        return new Ctx(tokens, batchId);
    }

    private void completeUpload(Ctx ctx) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "c.mp4", "contentType", "video/mp4", "sizeBytes", 128))))
                .andExpect(status().isCreated()).andReturn();
        long candidateId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("candidateId").asLong();
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM candidates WHERE id = ?", String.class, candidateId);
        byte[] mp4 = new byte[128];
        System.arraycopy("ftypisom".getBytes(), 0, mp4, 4, 8);
        storage.put(objectKey, mp4);
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
