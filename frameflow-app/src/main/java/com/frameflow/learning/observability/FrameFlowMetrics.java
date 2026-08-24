package com.frameflow.learning.observability;

import java.util.EnumMap;
import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Component;

/**
 * F10 业务指标入口。
 *
 * 【F10 阅读顺序】本类 → CorrelationIdFilter → AnalysisDispatchService /
 * AnalysisIngestionService → application.yml → infra/monitoring。
 *
 * 指标标签只接受 enum，禁止把 userId/batchId/runId 放进 label。那些 ID 应留在
 * 结构化日志；把高基数 ID 放进 Prometheus 会让时间序列数量随业务数据无限增长。
 */
@Component
public class FrameFlowMetrics {

    public enum DispatchOutcome {
        CONFIRMED("confirmed"), FAILED("failed");
        private final String tag;
        DispatchOutcome(String tag) { this.tag = tag; }
    }

    public enum IngestionOutcome {
        SUCCEEDED("succeeded"), ANALYSIS_ERROR("analysis_error"), DUPLICATE("duplicate");
        private final String tag;
        IngestionOutcome(String tag) { this.tag = tag; }
    }

    public enum RedisOperation {
        CACHE_READ("cache_read"), CACHE_WRITE("cache_write"), CACHE_EVICT("cache_evict"),
        RATE_LIMIT("rate_limit");
        private final String tag;
        RedisOperation(String tag) { this.tag = tag; }
    }

    private final Map<DispatchOutcome, Counter> dispatch;
    private final Map<IngestionOutcome, Counter> ingestion;
    private final Map<RedisOperation, Counter> redisDegradation;

    public FrameFlowMetrics(MeterRegistry registry) {
        dispatch = new EnumMap<>(DispatchOutcome.class);
        for (DispatchOutcome outcome : DispatchOutcome.values()) {
            dispatch.put(outcome, Counter.builder("frameflow.analysis.dispatch")
                    .description("Analysis task dispatch attempts by confirmed outcome")
                    .tag("outcome", outcome.tag)
                    .register(registry));
        }
        ingestion = new EnumMap<>(IngestionOutcome.class);
        for (IngestionOutcome outcome : IngestionOutcome.values()) {
            ingestion.put(outcome, Counter.builder("frameflow.analysis.ingestion")
                    .description("Idempotent analysis result ingestions by outcome")
                    .tag("outcome", outcome.tag)
                    .register(registry));
        }
        redisDegradation = new EnumMap<>(RedisOperation.class);
        for (RedisOperation operation : RedisOperation.values()) {
            redisDegradation.put(operation, Counter.builder("frameflow.dependency.degradation")
                    .description("Dependency degradation paths entered by dependency and operation")
                    .tag("dependency", "redis")
                    .tag("operation", operation.tag)
                    .register(registry));
        }
    }

    public void recordDispatch(DispatchOutcome outcome) {
        dispatch.get(outcome).increment();
    }

    public void recordIngestion(IngestionOutcome outcome) {
        ingestion.get(outcome).increment();
    }

    public void recordRedisDegradation(RedisOperation operation) {
        redisDegradation.get(operation).increment();
    }

    /** 仅供不启动 Spring 的既有单元测试构造降级组件；不注册到全局 Registry。 */
    public static FrameFlowMetrics isolatedForTest() {
        return new FrameFlowMetrics(new SimpleMeterRegistry());
    }
}
