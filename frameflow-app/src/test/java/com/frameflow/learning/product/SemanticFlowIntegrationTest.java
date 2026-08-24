package com.frameflow.learning.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.frameflow.learning.TestcontainersConfiguration;
import com.frameflow.learning.product.mq.AnalysisTaskMessage;
import com.frameflow.learning.product.mq.RabbitConfig;
import com.frameflow.learning.storage.MinioTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * F6 语义质检红线测试：语义结论永不自动淘汰，一律人工复核。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MinioTestConfig.class})
class SemanticFlowIntegrationTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private com.frameflow.learning.product.storage.StoragePort storage;

    @Test
    void semantic_unknown_leads_to_review_required_with_evidence() throws Exception {
        var ctx = preparedBatch("sem-unknown@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);

        ingest(task, List.of(
                passedDurationFinding(),
                semanticFinding("prompt_alignment", "UNKNOWN",
                        "{\"prompt\":\"对照 Brief...\",\"rawOutput\":\"模型不确定\"}")));

        mockMvc.perform(get("/api/v1/candidates/" + task.candidateId() + "/findings")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].dimension").value("prompt_alignment"))
                .andExpect(jsonPath("$[1].verdict").value("UNKNOWN"))
                .andExpect(jsonPath("$[1].evidence").value(
                        org.hamcrest.Matchers.containsString("rawOutput")));

        assertCandidateStatus(task.candidateId(), "REVIEW_REQUIRED");
    }

    @Test
    void semantic_violation_alone_is_review_not_auto_reject() throws Exception {
        var ctx = preparedBatch("sem-violate@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);

        // 语义 VIOLATE 且恶意/误报地标了 BLOCKER —— 接收端必须强制降级
        ingest(task, List.of(
                passedDurationFinding(),
                semanticFinding("policy_violation", "VIOLATE", "{\"rawOutput\":\"有水印\"}")));

        // 红线：语义从不触发 AUTO_REJECT
        assertCandidateStatus(task.candidateId(), "REVIEW_REQUIRED");
        Integer blockerRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ? "
                        + "AND verdict IS NOT NULL AND severity = 'BLOCKER'",
                Integer.class, task.candidateId());
        org.assertj.core.api.Assertions.assertThat(blockerRows).isZero();
    }

    @Test
    void semantic_blocker_severity_is_force_downgraded() throws Exception {
        var ctx = preparedBatch("sem-attack@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);

        // 恶意客户端：语义 Finding 标 BLOCKER 试图触发自动淘汰
        ingest(task, List.of(semanticFinding("prompt_alignment", "VIOLATE",
                "{\"rawOutput\":\"x\"}", "BLOCKER")));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM candidates WHERE id = ?", String.class, task.candidateId());
        org.assertj.core.api.Assertions.assertThat(status)
                .as("语义+伪造BLOCKER 仍不得 AUTO_REJECT").isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void semantic_detector_without_verdict_is_normalized_to_reviewable_error() throws Exception {
        var ctx = preparedBatch("sem-missing-verdict@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);

        // 模拟旧/异常 Worker：detector 已明确是 semantic，却遗漏 verdict，
        // 并伪造 passed=true + BLOCKER；接收端必须收敛成安全的 ERROR。
        ingest(task, List.of(Map.of(
                "detector", "semantic", "detectorVersion", "legacy",
                "dimension", "prompt_alignment", "passed", true,
                "severity", "BLOCKER", "evidence", "{}", "message", "missing verdict")));

        assertCandidateStatus(task.candidateId(), "REVIEW_REQUIRED");
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT passed, severity, verdict FROM findings WHERE candidate_id = ?",
                task.candidateId());
        org.assertj.core.api.Assertions.assertThat(stored.get("passed")).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(stored.get("severity")).isEqualTo("WARNING");
        org.assertj.core.api.Assertions.assertThat(stored.get("verdict")).isEqualTo("ERROR");
    }

    @Test
    void semantic_invalid_verdict_is_normalized_to_reviewable_error() throws Exception {
        var ctx = preparedBatch("sem-invalid-verdict@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);

        ingest(task, List.of(Map.of(
                "detector", "semantic", "detectorVersion", "broken",
                "dimension", "prompt_alignment", "passed", true,
                "severity", "BLOCKER", "verdict", "UNSUPPORTED",
                "evidence", "{}", "message", "invalid verdict")));

        assertCandidateStatus(task.candidateId(), "REVIEW_REQUIRED");
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT passed, severity, verdict FROM findings WHERE candidate_id = ?",
                task.candidateId());
        org.assertj.core.api.Assertions.assertThat(stored.get("passed")).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(stored.get("severity")).isEqualTo("WARNING");
        org.assertj.core.api.Assertions.assertThat(stored.get("verdict")).isEqualTo("ERROR");
    }

    @Test
    void semantic_all_pass_stays_analyzed() throws Exception {
        var ctx = preparedBatch("sem-pass@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);
        ingest(task, List.of(
                passedDurationFinding(),
                semanticFinding("prompt_alignment", "PASS", "{\"rawOutput\":\"对齐良好\"}")));
        assertCandidateStatus(task.candidateId(), "ANALYZED");
    }

    @Test
    void deterministic_blocker_wins_over_semantic() throws Exception {
        var ctx = preparedBatch("sem-mix@example.com");
        AnalysisTaskMessage task = dispatchAndReceive(ctx);
        ingest(task, List.of(
                Map.of("detector", "duration-rule", "detectorVersion", "1",
                        "dimension", "duration", "passed", false, "severity", "BLOCKER",
                        "evidence", "{\"durationMs\":1000}"),
                semanticFinding("prompt_alignment", "VIOLATE", "{}")));
        // 确定性 BLOCKER 是唯一的自动淘汰来源
        assertCandidateStatus(task.candidateId(), "AUTO_REJECT");
    }

    // ---------- 工具（与 AnalysisFlow 同套路，精简版） ----------

    private record Ctx(Map<String, Object> tokens, long batchId) { }

    private Ctx preparedBatch(String email) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"displayName\":\"x\"}"))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> tokens = objectMapper.readValue(reg.getResponse().getContentAsString(), Map.class);
        String auth = bearer(tokens);
        long projectId = idOf(mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"p-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"电商带货 brief：突出商品、不得有水印\"}"))
                .andExpect(status().isCreated());
        long profileId = idOf(mockMvc.perform(post("/api/v1/quality-profiles")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "s" + UUID.randomUUID()
                        .toString().substring(0, 6),
                        "spec", "{\"dimensions\":{\"duration\":{\"min\":5}},"
                                + "\"semantic\":{\"enabled\":true}}"))))
                .andExpect(status().isCreated()).andReturn());
        long batchId = idOf(mockMvc.perform(post("/api/v1/batches")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectId", projectId, "profileId", profileId, "capacity", 2))))
                .andExpect(status().isCreated()).andReturn());

        // 上传一个候选并关闭批次
        MvcResult cand = mockMvc.perform(post("/api/v1/batches/" + batchId + "/candidates")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "c.mp4", "contentType", "video/mp4", "sizeBytes", 128))))
                .andExpect(status().isCreated()).andReturn();
        long candidateId = objectMapper.readTree(cand.getResponse().getContentAsString())
                .get("candidateId").asLong();
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM candidates WHERE id = ?", String.class, candidateId);
        byte[] mp4 = new byte[128];
        System.arraycopy("ftypisom".getBytes(), 0, mp4, 4, 8);
        storage.put(objectKey, mp4);
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/batches/" + batchId + "/close")
                        .header("Authorization", auth)).andExpect(status().isOk());
        return new Ctx(tokens, batchId);
    }

    private AnalysisTaskMessage dispatchAndReceive(Ctx ctx) throws Exception {
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/analyze")
                        .header("Authorization", bearer(ctx.tokens())))
                .andExpect(status().isAccepted());
        Message raw = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        AnalysisTaskMessage task = objectMapper.readValue(raw.getBody(), AnalysisTaskMessage.class);
        // F6：消息自包含 Brief（语义对齐的输入）
        org.assertj.core.api.Assertions.assertThat(task.briefContent()).contains("brief");
        return task;
    }

    private void ingest(AnalysisTaskMessage task, List<Map<String, Object>> findings)
            throws Exception {
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", task.runId(), "workerVersion", "f6-test",
                                "ok", true, "durationMs", 8000,
                                "width", 1080, "height", 1920, "fps", 30.0,
                                "findings", findings))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> passedDurationFinding() {
        return Map.of("detector", "duration-rule", "detectorVersion", "1",
                "dimension", "duration", "passed", true, "severity", "INFO",
                "evidence", "{\"durationMs\":8000}");
    }

    private Map<String, Object> semanticFinding(String dimension, String verdict,
                                                String evidence) {
        return semanticFinding(dimension, verdict, evidence, "WARNING");
    }

    private Map<String, Object> semanticFinding(String dimension, String verdict,
                                                String evidence, String severity) {
        return Map.of("detector", "semantic-openai-compat", "detectorVersion", "1",
                "dimension", dimension, "passed", verdict.equals("PASS"),
                "severity", severity, "verdict", verdict,
                "evidence", evidence, "message", "语义判定: " + verdict);
    }

    private void assertCandidateStatus(long candidateId, String expected) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM candidates WHERE id = ?", String.class, candidateId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo(expected);
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
