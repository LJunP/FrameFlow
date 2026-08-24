package com.frameflow.learning.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameFlowMetricsTest {

    @Test
    void exposes_finite_business_and_degradation_labels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new FrameFlowMetrics(registry);

        metrics.recordDispatch(FrameFlowMetrics.DispatchOutcome.CONFIRMED);
        metrics.recordIngestion(FrameFlowMetrics.IngestionOutcome.ANALYSIS_ERROR);
        metrics.recordRedisDegradation(FrameFlowMetrics.RedisOperation.CACHE_READ);

        assertThat(registry.get("frameflow.analysis.dispatch")
                .tag("outcome", "confirmed").counter().count()).isEqualTo(1);
        assertThat(registry.get("frameflow.analysis.ingestion")
                .tag("outcome", "analysis_error").counter().count()).isEqualTo(1);
        assertThat(registry.get("frameflow.dependency.degradation")
                .tags("dependency", "redis", "operation", "cache_read")
                .counter().count()).isEqualTo(1);
        assertThat(registry.find("frameflow.analysis.dispatch").counters()).hasSize(2);
        assertThat(registry.find("frameflow.analysis.ingestion").counters()).hasSize(3);
    }
}
