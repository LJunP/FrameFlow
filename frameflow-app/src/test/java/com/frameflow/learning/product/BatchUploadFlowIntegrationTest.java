package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.frameflow.learning.TestcontainersConfiguration;
import com.frameflow.learning.storage.MinioTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.frameflow.learning.product.storage.StoragePort;

/**
 * F3 批次与上传集成测试：真实 PostgreSQL + 真实 MinIO 容器，
 * presigned URL 用 java.net.http.HttpClient 真的 PUT——端到端证明直传可用。
 */
@SpringBootTest(properties = {
        "frameflow.storage.multipart-threshold=4MB",   // 压低阈值让分片路径可测
        "frameflow.storage.part-size=5MB"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MinioTestConfig.class})
class BatchUploadFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoragePort storage;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ---------- 完整简单直传流 ----------

    @Test
    void simple_upload_end_to_end() throws Exception {
        var ctx = preparedContext("simple@example.com");

        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "clip-01.mp4",
                                "contentType", "video/mp4",
                                "sizeBytes", 256))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("SIMPLE"))
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andReturn();
        var regBody = objectMapper.readValue(reg.getResponse().getContentAsString(), Map.class);
        long candidateId = ((Number) regBody.get("candidateId")).longValue();

        // ★ 端到端的关键：拿着服务端签发的 URL 真的 PUT 成功
        byte[] mp4 = validMp4Header(256);
        putRaw((String) regBody.get("uploadUrl"), mp4, "video/mp4");

        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void non_video_bytes_become_invalid_with_evidence() throws Exception {
        var ctx = preparedContext("bad@example.com");
        long candidateId = registerSimple(ctx, "not-a-video.mp4", 128);

        // 128 字节的"文本"（大小与登记一致，专门让媒体签名检查接锅）
        byte[] notVideo = new byte[128];
        System.arraycopy("hello, i am a text file".getBytes(), 0, notVideo, 0, 23);
        putRaw(uploadUrlOf(ctx, candidateId), notVideo, "video/mp4");

        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.probeError").value(org.hamcrest.Matchers.containsString("媒体签名")));

        // 证据在候选列表里留痕
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(jsonPath("$.items[0].status").value("INVALID"))
                .andExpect(jsonPath("$.items[0].probeError").isNotEmpty());
    }

    @Test
    void size_mismatch_becomes_invalid() throws Exception {
        var ctx = preparedContext("size@example.com");
        long candidateId = registerSimple(ctx, "liar.mp4", 1024);   // 登记 1KB
        putRaw(uploadUrlOf(ctx, candidateId), validMp4Header(64), "video/mp4"); // 实传 64B

        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.probeError").value(org.hamcrest.Matchers.containsString("大小不匹配")));
    }

    @Test
    void complete_without_upload_is_400() throws Exception {
        var ctx = preparedContext("ghost@example.com");
        long candidateId = registerSimple(ctx, "ghost.mp4", 128);

        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
    }

    // ---------- 分片上传 ----------

    @Test
    void multipart_upload_end_to_end() throws Exception {
        var ctx = preparedContext("mp@example.com");
        // 分片大小必须精确等于各片之和（完成时做总大小校验）：
        // 5MB + 4MB = 9,437,184 字节
        long partSize = 5 * 1024 * 1024;
        long size = partSize + 4 * 1024 * 1024;   // > 4MB 阈值 → 分片，2 片

        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "big-clip.mp4",
                                "contentType", "video/mp4",
                                "sizeBytes", size))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("MULTIPART"))
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.partSizeBytes").value(5 * 1024 * 1024))
                .andReturn();
        var regBody = objectMapper.readValue(reg.getResponse().getContentAsString(), Map.class);
        long candidateId = ((Number) regBody.get("candidateId")).longValue();

        // 领两片的上传 URL
        MvcResult partsResp = mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/upload-parts")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNumbers\":[1,2]}"))
                .andExpect(status().isOk())
                .andReturn();
        var partUrls = (Map<String, String>) objectMapper
                .readValue(partsResp.getResponse().getContentAsString(), Map.class)
                .get("partUrls");
        assertThat(partUrls).hasSize(2);

        // 真实上传两片（第一片带头部，大小之和必须等于登记值）
        byte[] part1 = validMp4Header((int) partSize);
        byte[] part2 = new byte[(int) (size - partSize)];
        String etag1 = putRaw(partUrls.get("1"), part1, "video/mp4");
        String etag2 = putRaw(partUrls.get("2"), part2, "video/mp4");

        // 缺片先被拒：只交第 2 片 → 分片号不连续
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parts",
                                List.of(Map.of("partNumber", 2, "etag", etag2))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARTS_INVALID"));

        // 齐片合并成功
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parts", List.of(
                                Map.of("partNumber", 1, "etag", etag1),
                                Map.of("partNumber", 2, "etag", etag2))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    // ---------- 登记校验 ----------

    @Test
    void register_validations() throws Exception {
        var ctx = preparedContext("reg@example.com");

        // 非 video/* → 400
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT_TYPE"));

        // 容量 1：第一笔成功，第二笔 409 BATCH_FULL
        long c1 = registerSimple(ctx, "only-one.mp4", 64);
        assertThat(c1).isPositive();
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"second.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":64}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATCH_FULL"));

        // 关闭后再登记 → 409 BATCH_CLOSED
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/close")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"after.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":64}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATCH_CLOSED"));
    }

    @Test
    void batch_binds_latest_profile_version_and_current_brief() throws Exception {
        var ctx = preparedContext("bind@example.com");
        mockMvc.perform(get("/api/v1/batches/" + ctx.batchId)
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersionNo").value(1))
                .andExpect(jsonPath("$.briefId").value(ctx.briefId));
    }

    // ---------- 对账 ----------

    @Test
    void reconcile_reports_missing_orphan_and_invalidates_stale() throws Exception {
        var ctx = preparedContext("rec@example.com");

        // 1) 完成一笔正常上传
        long ok = registerSimple(ctx, "ok.mp4", 128);
        putRaw(uploadUrlOf(ctx, ok), validMp4Header(128), "video/mp4");
        complete(ctx, ok);

        // 2) 对象被误删（模拟存储故障）→ missingObjects
        String objectKey = candidateObjectKey(ctx, ok);
        storage.delete(objectKey);

        // 3) 孤儿对象（绕过 API 直接写 bucket）
        storage.put("batches/" + ctx.batchId + "/candidates/orphan-xyz.bin", new byte[10]);

        // 4) 超时的待上传登记（created_at 拨回 3 小时前）
        jdbcTemplate.update("INSERT INTO candidates"
                        + "(batch_id, file_name, content_type, size_bytes, object_key, created_by, created_at) "
                        + "VALUES(?, 'stale.mp4', 'video/mp4', 10, 'batches/' || ? || '/candidates/stale-x/stale.mp4', "
                        + "(SELECT id FROM users WHERE email='rec@example.com'), now() - interval '3 hours')",
                ctx.batchId, ctx.batchId);

        MvcResult report = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/reconcile")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingObjects").value(1))
                .andExpect(jsonPath("$.orphanObjects").value(1))
                .andExpect(jsonPath("$.invalidatedStalePending").value(1))
                .andExpect(jsonPath("$.missingObjectKeys[0]").value(objectKey))
                .andReturn();
        assertThat(report.getResponse().getContentAsString()).contains("orphan-xyz.bin");
    }

    // ---------- 工具 ----------

    private record Ctx(Map<String, Object> tokens, long batchId, long briefId) { }

    /** 建全套前置：团队 + 项目 + Brief + Profile + 批次（容量 1，其余测试自建）。 */
    private Ctx preparedContext(String email) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                                + "\"displayName\":\"x\"}"))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> tokens = objectMapper.readValue(reg.getResponse().getContentAsString(), Map.class);
        String auth = bearer(tokens);

        long projectId = idOf(mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"p-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn());

        long briefId = idOf(mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"brief-v1\"}"))
                .andExpect(status().isCreated()).andReturn());

        long profileId = idOf(mockMvc.perform(post("/api/v1/quality-profiles")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "std-" + UUID.randomUUID()
                        .toString().substring(0, 6), "spec", "{\"dimensions\":{}}"))))
                .andExpect(status().isCreated()).andReturn());

        long batchId = idOf(mockMvc.perform(post("/api/v1/batches")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectId", projectId, "profileId", profileId, "capacity", 1))))
                .andExpect(status().isCreated()).andReturn());

        return new Ctx(tokens, batchId, briefId);
    }

    private long registerSimple(Ctx ctx, String fileName, int size) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", fileName, "contentType", "video/mp4", "sizeBytes", size))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("SIMPLE"))
                .andReturn();
        // 登记响应的字段是 candidateId（不是 id）
        return objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("candidateId").asLong();
    }

    /**
     * 登记时签发的 URL 没有落库（一次性凭证）；
     * 测试里直接对同一 objectKey 重新签一个等价 URL（S3 语义：同 key 可多次预签名）。
     */
    private String uploadUrlOf(Ctx ctx, long candidateId) {
        return storage.presignPut(candidateObjectKey(ctx, candidateId), java.time.Duration.ofMinutes(10));
    }

    private String candidateObjectKey(Ctx ctx, long candidateId) {
        return jdbcTemplate.queryForObject(
                "SELECT object_key FROM candidates WHERE id = ?", String.class, candidateId);
    }

    private void complete(Ctx ctx, long candidateId) throws Exception {
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    /** 真实 HTTP PUT 到 presigned URL；返回 ETag（分片测试用）。 */
    private String putRaw(String url, byte[] body, String contentType) throws Exception {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                        .header("Content-Type", contentType)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).as("presigned PUT 应成功: %s", resp.body()).isEqualTo(200);
        return resp.headers().firstValue("etag").orElse(null);
    }

    /** 构造带 ftyp 签名但不要求可播放的最小 MP4 头（测试专用）。 */
    private byte[] validMp4Header(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy("ftypisom".getBytes(), 0, bytes, 4, 8);
        return bytes;
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
