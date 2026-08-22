package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * F7 排名与优选：聚类正确性、可复现性、锁定不可变、导出。
 * 测试扮演 worker：通过内部回写接口注入指纹（contentHash/phash）与 Finding，
 * 以精确控制聚类与分数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MinioTestConfig.class})
class RankingSelectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- 聚类 + 排名 ----------

    @Test
    void cluster_rank_and_reproducibility() throws Exception {
        var ctx = preparedBatch();
        long a = uploadAndIngest(ctx, "a.mp4", "hash-AAA", "0000000000000001", 100);
        long b = uploadAndIngest(ctx, "b.mp4", "hash-AAA", "0000000000000002", 100);  // 精确重复
        long c = uploadAndIngest(ctx, "c.mp4", "hash-CCC", "0000000000000003", 90);   // 近重复(距离≤1)+扣分
        long d = uploadAndIngest(ctx, "d.mp4", "hash-DDD", "ffffffffffffffff", 100);  // 独立簇

        MvcResult rank1 = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/rank")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clusters").value(2))
                .andExpect(jsonPath("$.ranked").value(2))
                .andExpect(jsonPath("$.excluded").value(0))
                .andReturn();
        long snapshot1 = objectMapper.readTree(rank1.getResponse().getContentAsString())
                .get("snapshotId").asLong();

        MvcResult latest = mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/ranking/latest")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> entries = objectMapper.readValue(
                latest.getResponse().getContentAsString(), Map.class)
                .get("entries") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();

        var byId = new java.util.HashMap<Long, Map<String, Object>>();
        for (Map<String, Object> e : entries) {
            byId.put(((Number) e.get("candidateId")).longValue(), e);
        }
        // A 与 B 同分并列 → candidateId 小者代表；C 近重复并入 A 簇；D 独立
        assertThat(byId.get(a).get("rankNo")).isEqualTo(1);
        assertThat(byId.get(b).get("rankNo")).isEqualTo(0);
        assertThat(byId.get(b).get("excludedReason")).isEqualTo("DUPLICATE_OF_" + a);
        assertThat(byId.get(c).get("rankNo")).isEqualTo(0);
        assertThat(byId.get(c).get("excludedReason")).isEqualTo("DUPLICATE_OF_" + a);
        assertThat(byId.get(d).get("rankNo")).isEqualTo(2);
        assertThat(byId.get(a).get("clusterId")).isEqualTo(byId.get(b).get("clusterId"));
        assertThat(byId.get(a).get("clusterId")).isEqualTo(byId.get(c).get("clusterId"));
        assertThat(byId.get(d).get("clusterId")).isNotEqualTo(byId.get(a).get("clusterId"));
        // C 被扣 10 分（resolution WARNING，权重来自 spec.weights）
        assertThat(byId.get(c).get("score")).isEqualTo(90);
        assertThat(byId.get(a).get("score")).isEqualTo(100);

        // 可复现：再排一次 → 两个快照的"业务内容"完全一致
        //（剥离 id/snapshotId 这类自增代理键后再比）
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/rank")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isCreated());
        MvcResult latest2 = mockMvc.perform(get("/api/v1/batches/" + ctx.batchId + "/ranking/latest")
                        .header("Authorization", bearer(ctx.tokens)))
                .andReturn();
        List<Map<String, Object>> entries2 = (List<Map<String, Object>>) ((Map<String, Object>)
                objectMapper.readValue(latest2.getResponse().getContentAsString(), Map.class))
                .get("entries");
        assertThat(normalize(entries2)).isEqualTo(normalize(entries));

        // 顺手验证优选集：topK=2 → 机器选 A(rank1) 与 D(rank2)
        MvcResult created = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/selections")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topK\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long selectionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        MvcResult detail = mockMvc.perform(get("/api/v1/selections/" + selectionId)
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<String, Object>)
                objectMapper.readValue(detail.getResponse().getContentAsString(), Map.class))
                .get("items");
        assertThat(items).hasSize(2);
        assertThat(items.stream().map(i -> ((Number) i.get("candidateId")).longValue()))
                .containsExactlyInAnyOrder(a, d);
        assertThat(items.get(0).get("machinePick")).isEqualTo(true);
    }

    @Test
    void gate_excludes_auto_reject_and_error() throws Exception {
        var ctx = preparedBatch();
        long ok = uploadAndIngest(ctx, "ok.mp4", "h1", "0000000000000001", 100);
        // BLOCKER → AUTO_REJECT（资格门外）
        long cid = upload(ctx, "bad.mp4");
        ingest(cid, ctx.batchId, Map.of(
                "runId", runOf(cid), "workerVersion", "w", "ok", true,
                "durationMs", 8000, "width", 1080, "height", 1920, "fps", 30.0,
                "contentHash", "h2", "phash", "0000000000000002",
                "findings", List.of(Map.of("detector", "duration-rule", "detectorVersion", "1",
                        "dimension", "duration", "passed", false, "severity", "BLOCKER",
                        "evidence", "{}", "message", "超时"))));
        // ANALYSIS_ERROR（系统故障也不参排）
        long err = upload(ctx, "err.mp4");
        ingest(err, ctx.batchId, Map.of("runId", runOf(err), "workerVersion", "w",
                "ok", false, "errorSummary", "probe failed"));

        MvcResult latest = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/rank")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(jsonPath("$.ranked").value(1))
                .andExpect(jsonPath("$.excluded").value(2))
                .andReturn();
        assertThat(latest.getResponse().getContentAsString()).isNotEmpty();
    }

    // ---------- 人工叠加 + 锁定 + 导出 ----------

    @Test
    void overlay_lock_and_export() throws Exception {
        var ctx = preparedBatch();
        long a = uploadAndIngest(ctx, "a.mp4", "h1", "0000000000000001", 100);
        long d = uploadAndIngest(ctx, "d.mp4", "h2", "ffffffffffffffff", 100);
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/rank")
                .header("Authorization", bearer(ctx.tokens))).andExpect(status().isCreated());
        long selectionId = idOf(mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/selections")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topK\":2}"))
                .andExpect(status().isCreated()).andReturn());

        // 人工叠加：EXCLUDE rank1 的机器选择；INCLUDE 机器没选的 C
        long c = uploadAndIngest(ctx, "c.mp4", "h3", "0000000000000003", 90);
        mockMvc.perform(post("/api/v1/selections/" + selectionId + "/items")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "candidateId", a, "action", "EXCLUDE", "note", "画面重复"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/selections/" + selectionId + "/items")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "candidateId", c, "action", "INCLUDE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        // 锁定前导出 → 409 NOT_LOCKED
        mockMvc.perform(get("/api/v1/selections/" + selectionId + "/export")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isConflict());

        // 锁定 → 不可再改/再锁
        mockMvc.perform(post("/api/v1/selections/" + selectionId + "/lock")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        mockMvc.perform(post("/api/v1/selections/" + selectionId + "/lock")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELECTION_LOCKED"));
        mockMvc.perform(post("/api/v1/selections/" + selectionId + "/items")
                        .header("Authorization", bearer(ctx.tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "candidateId", d, "action", "EXCLUDE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELECTION_LOCKED"));

        // 导出 CSV：机器列与人工列并存（审计可还原）
        MvcResult csv = mockMvc.perform(get("/api/v1/selections/" + selectionId + "/export")
                        .queryParam("format", "csv")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andReturn();
        String body = csv.getResponse().getContentAsString();
        assertThat(body).contains("rank,candidate_id,score,cluster_id,machine_pick,human_action,note");
        assertThat(body).contains("true,EXCLUDE");          // 机器选了且被人工排除
        assertThat(body).contains("false,INCLUDE");         // 机器没选但被人工加进

        mockMvc.perform(get("/api/v1/selections/" + selectionId + "/export")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk());   // JSON 默认
    }

    // ---------- 工具：本测试扮演 worker ----------

    private record Ctx(Map<String, Object> tokens, long batchId) { }

    private Ctx preparedBatch() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rk" + UUID.randomUUID().toString().substring(0, 6)
                                + "@example.com\",\"password\":\"Passw0rd!\",\"displayName\":\"x\"}"))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> tokens = objectMapper.readValue(
                reg.getResponse().getContentAsString(), Map.class);
        String auth = bearer(tokens);
        long projectId = idOf(mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"p-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"brief\"}")).andExpect(status().isCreated());
        long profileId = idOf(mockMvc.perform(post("/api/v1/quality-profiles")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "s" + UUID.randomUUID()
                        .toString().substring(0, 6),
                        "spec", "{\"weights\":{\"resolution\":10},"
                                + "\"duplicates\":{\"hammingThreshold\":6}}"))))
                .andExpect(status().isCreated()).andReturn());
        long batchId = idOf(mockMvc.perform(post("/api/v1/batches")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectId", projectId, "profileId", profileId, "capacity", 10))))
                .andExpect(status().isCreated()).andReturn());
        return new Ctx(tokens, batchId);
    }

    private long upload(Ctx ctx, String fileName) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", fileName, "contentType", "video/mp4",
                                "sizeBytes", 64))))
                .andExpect(status().isCreated()).andReturn();
        long candidateId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("candidateId").asLong();
        jdbcTemplate.update("UPDATE candidates SET status='ANALYZED' WHERE id=?", candidateId);
        return candidateId;
    }

    /** 上传 + 注入带指纹的分析结果；expectedScore 100=全过，90=一个 resolution WARNING。 */
    private long uploadAndIngest(Ctx ctx, String fileName, String contentHash,
                                 String phash, int expectedScore) throws Exception {
        long candidateId = upload(ctx, fileName);
        Object findings = expectedScore >= 100
                ? List.of()
                : List.of(Map.of("detector", "spec-rules", "detectorVersion", "1",
                        "dimension", "resolution", "passed", false, "severity", "WARNING",
                        "evidence", "{}", "message", "分辨率不足"));
        ingest(candidateId, ctx.batchId, Map.of(
                "runId", runOf(candidateId), "workerVersion", "w", "ok", true,
                "durationMs", 8000, "width", 1080, "height", 1920, "fps", 30.0,
                "contentHash", contentHash, "phash", phash,
                "findings", findings));
        return candidateId;
    }

    private long runOf(long candidateId) {
        var existing = jdbcTemplate.queryForList(
                "SELECT id FROM analysis_runs WHERE candidate_id=? ORDER BY id DESC LIMIT 1",
                Long.class, candidateId);
        if (!existing.isEmpty()) {
            jdbcTemplate.update("UPDATE analysis_runs SET status='SUCCEEDED' WHERE id=?",
                    existing.get(0));
            return existing.get(0);
        }
        Long batchId = jdbcTemplate.queryForObject(
                "SELECT batch_id FROM candidates WHERE id=?", Long.class, candidateId);
        jdbcTemplate.update(
                "INSERT INTO analysis_runs(candidate_id, batch_id) VALUES(?, ?)",
                candidateId, batchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM analysis_runs WHERE candidate_id=? ORDER BY id DESC LIMIT 1",
                Long.class, candidateId);
    }

    private void ingest(long candidateId, long batchId, Map<String, Object> payload)
            throws Exception {
        // 状态回 ANALYZING 让条件迁移生效（upload 工具直接设了 ANALYZED）
        jdbcTemplate.update("UPDATE candidates SET status='ANALYZING' WHERE id=?", candidateId);
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    /** 剥离代理键，保留业务字段——可复现性比的就是这些。 */
    private List<String> normalize(List<Map<String, Object>> entries) {
        return entries.stream()
                .map(e -> String.join("|",
                        String.valueOf(e.get("candidateId")),
                        String.valueOf(e.get("rankNo")),
                        String.valueOf(e.get("clusterId")),
                        String.valueOf(e.get("score")),
                        String.valueOf(e.get("representative")),
                        String.valueOf(e.get("excludedReason")),
                        String.valueOf(e.get("breakdown"))))
                .toList();
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
