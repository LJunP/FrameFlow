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
 * F4 流水线集成测试：真实 RabbitMQ 容器；测试进程扮演 worker——
 * 从队列取任务、调用内部回写接口，覆盖"消息不丢、结果不重、错误不伪装"。
 */
@SpringBootTest(properties = {"frameflow.storage.multipart-threshold=4MB"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MinioTestConfig.class})
class AnalysisFlowIntegrationTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        // 容器默认凭据是 guest/guest，必须覆盖 yml 里的开发值，否则 ACCESS_REFUSED
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
    private org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    // ---------- 全链路：派发 → 取任务 → 回写 → AUTO_REJECT + 证据 ----------

    @Test
    void dispatch_consume_ingest_end_to_end_with_blocker_finding() throws Exception {
        var ctx = preparedBatch("e2e@example.com", 2);
        long candidateId = uploadOne(ctx, "clip.mp4", 256);

        MvcResult analyze = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/analyze")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.dispatched").value(1))
                .andReturn();

        // worker 视角：从队列取到任务，内容自包含（runId/objectKey/标准）
        Message raw = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        assertThat(raw).isNotNull();
        AnalysisTaskMessage task = objectMapper.readValue(raw.getBody(), AnalysisTaskMessage.class);
        assertThat(task.candidateId()).isEqualTo(candidateId);
        assertThat(task.objectKey()).contains("batches/" + ctx.batchId + "/candidates/");
        assertThat(task.profileSpec()).contains("duration");

        // worker 判定：时长 3s，标准要求 5..20s → BLOCKER 未通过
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", task.runId(),
                                "workerVersion", "py-worker-0.1.0",
                                "ok", true,
                                "durationMs", 3000,
                                "width", 1080, "height", 1920, "fps", 30.0,
                                "findings", List.of(Map.of(
                                        "detector", "duration-rule",
                                        "detectorVersion", "1",
                                        "dimension", "duration",
                                        "passed", false,
                                        "severity", "BLOCKER",
                                        "evidence", "{\"durationMs\":3000,\"min\":5,\"max\":20}",
                                        "message", "时长 3.0s 不在 [5,20]s 区间"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateStatus").value("AUTO_REJECT"))
                .andExpect(jsonPath("$.duplicate").value(false));

        // 证据可查：Finding 带证据落库，探针字段回填
        mockMvc.perform(get("/api/v1/candidates/" + candidateId + "/findings")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dimension").value("duration"))
                .andExpect(jsonPath("$[0].passed").value(false))
                .andExpect(jsonPath("$[0].evidence").value(org.hamcrest.Matchers.containsString("3000")));
        Integer duration = jdbcTemplate.queryForObject(
                "SELECT duration_ms FROM candidates WHERE id = ?", Integer.class, candidateId);
        assertThat(duration).isEqualTo(3000);
    }

    // ---------- 幂等：重复回写不产生重复 Finding ----------

    @Test
    void duplicated_result_is_idempotent() throws Exception {
        var ctx = preparedBatch("dup@example.com", 2);
        long candidateId = uploadOne(ctx, "dup.mp4", 256);
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/analyze")
                .header("Authorization", bearer(ctx.tokens))).andExpect(status().isAccepted());
        Message raw = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        AnalysisTaskMessage task = objectMapper.readValue(raw.getBody(), AnalysisTaskMessage.class);

        String body = objectMapper.writeValueAsString(Map.of(
                "runId", task.runId(), "workerVersion", "w", "ok", true,
                "durationMs", 8000, "width", 1080, "height", 1920, "fps", 30.0,
                "findings", List.of(Map.of("detector", "duration-rule", "detectorVersion", "1",
                        "dimension", "duration", "passed", true, "severity", "INFO",
                        "evidence", "{\"durationMs\":8000}", "message", "时长合规"))));

        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.candidateStatus").value("ANALYZED"))
                .andExpect(jsonPath("$.duplicate").value(false));

        // 完全相同的回写再来一次（模拟消息重投/客户端重试）
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.candidateStatus").value("ANALYZED"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ?", Integer.class, candidateId);
        assertThat(count).isEqualTo(1);
    }

    // ---------- 红线：worker 故障 = ANALYSIS_ERROR，绝不 AUTO_REJECT ----------

    @Test
    void worker_failure_is_analysis_error_not_auto_reject() throws Exception {
        var ctx = preparedBatch("err@example.com", 2);
        uploadOne(ctx, "broken.mp4", 256);
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/analyze")
                .header("Authorization", bearer(ctx.tokens))).andExpect(status().isAccepted());
        Message raw = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        AnalysisTaskMessage task = objectMapper.readValue(raw.getBody(), AnalysisTaskMessage.class);

        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", task.runId(), "workerVersion", "w",
                                "ok", false,
                                "errorSummary", "ffprobe 进程退出码 1: moov atom not found"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateStatus").value("ANALYSIS_ERROR"));

        Integer findings = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ?", Integer.class, task.candidateId());
        assertThat(findings).isZero();
    }

    // ---------- 内部接口鉴权 ----------

    @Test
    void internal_result_requires_worker_key() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":1,\"workerVersion\":\"w\",\"ok\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":1,\"workerVersion\":\"w\",\"ok\":true}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 崩溃重投：不丢任务、不重复结论 ----------

    @Test
    void crashed_consumer_redelivers_and_idempotency_holds() throws Exception {
        var ctx = preparedBatch("crash@example.com", 2);
        long candidateId = uploadOne(ctx, "crash.mp4", 256);
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/analyze")
                .header("Authorization", bearer(ctx.tokens))).andExpect(status().isAccepted());

        // 模拟 worker 拿到消息后崩溃：用【物理连接】basicGet(手动 ack 模式)
        // 拿到消息后不 ack 直接关连接——broker 把未确认消息重新入队。
        // ★ 两个坑：1) 模板 execute 的通道来自缓存池不会真关闭；
        // 2) 即便 createConnection() 拿到的也是缓存代理，close() 只是归还
        // 连接池、物理通道还活着——必须走 getRabbitConnectionFactory()
        // 建真连接，close 才会触发 broker 重投。
        var caching = (org.springframework.amqp.rabbit.connection.CachingConnectionFactory)
                rabbitConnectionFactory;
        com.rabbitmq.client.Connection physical =
                caching.getRabbitConnectionFactory().newConnection();
        var channel = physical.createChannel();
        var got = channel.basicGet(RabbitConfig.TASK_QUEUE, false);
        assertThat(got).as("应先取到消息").isNotNull();
        physical.close();

        // 消息被重新投递（没丢），现在正常处理
        Message raw = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        assertThat(raw).isNotNull();
        AnalysisTaskMessage task = objectMapper.readValue(raw.getBody(), AnalysisTaskMessage.class);

        String body = objectMapper.writeValueAsString(Map.of(
                "runId", task.runId(), "workerVersion", "w", "ok", true,
                "durationMs", 9000, "width", 1080, "height", 1920, "fps", 30.0,
                "findings", List.of()));
        mockMvc.perform(post("/api/v1/internal/analysis-results")
                        .header("X-Worker-Key", "frameflow-dev-worker-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.candidateStatus").value("ANALYZED"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ?", Integer.class, candidateId);
        assertThat(count).isZero();   // 本例 findings 为空，重点是不丢不重
        Integer runRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_runs WHERE candidate_id = ?", Integer.class, candidateId);
        assertThat(runRows).isEqualTo(1);   // 没有因重投产生第二个 run
    }

    // ---------- DLQ：深度统计与重放 ----------

    @Test
    void dlq_stats_and_replay() throws Exception {
        var ctx = preparedBatch("dlq@example.com", 2);

        // 先确保拓扑已声明（惰性声明在首次建连时，别赌时序）
        rabbitAdmin.initialize();
        // 直接把一条消息塞进死信交换机（模拟被 reject 的毒消息）
        rabbitTemplate.convertAndSend(RabbitConfig.DLX_EXCHANGE, "analysis.dead",
                Map.of("runId", -1, "note", "poison"));

        // 用"收一条"验证路由确实通了（比查深度更直接；收到后放回去供
        // stats/replay 接口消费）
        Message dead = rabbitTemplate.receive(RabbitConfig.DLQ_QUEUE, 5000);
        assertThat(dead).as("消息应已路由到 DLQ（若为 null，检查 DLX 绑定与 routing key）").isNotNull();
        rabbitTemplate.send(RabbitConfig.DLX_EXCHANGE, "analysis.dead", dead);

        // 等异步发布落地（correlated confirm 下 template.send 立即返回）
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            var props = rabbitAdmin.getQueueProperties(RabbitConfig.DLQ_QUEUE);
            int depth = props == null ? 0
                    : Integer.parseInt(props.getProperty("QUEUE_MESSAGE_COUNT", "0"));
            if (depth >= 1) {
                break;
            }
            Thread.sleep(100);
        }

        mockMvc.perform(get("/api/v1/admin/mq/stats")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskQueueDepth").exists())
                .andExpect(jsonPath("$.dlqDepth").exists());

        mockMvc.perform(post("/api/v1/admin/mq/replay-dlq")
                        .header("Authorization", bearer(ctx.tokens)))
                .andExpect(status().isOk());

        // 重放的实质验证：消息回到主队列（内容是塞进 DLQ 的那条毒消息）
        Message replayed = rabbitTemplate.receive(RabbitConfig.TASK_QUEUE, 5000);
        assertThat(replayed).isNotNull();
        assertThat(new String(replayed.getBody())).contains("poison");
    }

    // ---------- 工具 ----------

    private record Ctx(Map<String, Object> tokens, long batchId) { }

    private Ctx preparedBatch(String email, int capacity) throws Exception {
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
                .content("{\"content\":\"b\"}")).andExpect(status().isCreated());
        long profileId = idOf(mockMvc.perform(post("/api/v1/quality-profiles")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "s-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                        "spec", "{\"dimensions\":{\"duration\":{\"min\":5,\"max\":20}}}"))))
                .andExpect(status().isCreated()).andReturn());
        long batchId = idOf(mockMvc.perform(post("/api/v1/batches")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectId", projectId, "profileId", profileId, "capacity", capacity))))
                .andExpect(status().isCreated()).andReturn());
        return new Ctx(tokens, batchId);
    }

    /** 登记并完成一笔真实上传（借 MinIO 容器走直传链路）。 */
    @Autowired
    com.frameflow.learning.product.storage.StoragePort storage;

    private long uploadOne(Ctx ctx, String fileName, int size) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/candidates")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", fileName, "contentType", "video/mp4", "sizeBytes", size))))
                .andExpect(status().isCreated()).andReturn();
        long candidateId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("candidateId").asLong();
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM candidates WHERE id = ?", String.class, candidateId);
        byte[] mp4 = new byte[size];
        System.arraycopy("ftypisom".getBytes(), 0, mp4, 4, 8);
        storage.put(objectKey, mp4);
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/complete")
                        .header("Authorization", bearer(ctx.tokens()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
        mockMvc.perform(post("/api/v1/batches/" + ctx.batchId + "/close")
                        .header("Authorization", bearer(ctx.tokens())))
                .andExpect(status().isOk());
        return candidateId;
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
