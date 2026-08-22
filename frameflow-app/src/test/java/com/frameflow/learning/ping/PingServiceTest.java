package com.frameflow.learning.ping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.frameflow.learning.config.AppInfoProperties;

/**
 * 纯单元测试：不起 Spring、直接 new，毫秒级跑完。
 * 与 PingControllerIntegrationTest 对比着读，体会"单元测试"与"集成测试"
 * 的代价和覆盖面差异。
 */
class PingServiceTest {

    // ★ 核心：固定时钟让"时间"从变量变成常量——这是 PingService 注入 Clock
    // （见其 ★ 注释）在测试侧的兑现。断言因此可以精确、稳定、可复现。
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-08-22T12:00:30Z"), ZoneOffset.UTC);

    private final PingService service =
            new PingService(new AppInfoProperties("frameflow-select", "0.1.0-SNAPSHOT"), fixedClock);

    @Test
    void ping_carries_config_values() {
        PingResponse resp = service.buildPing();

        assertThat(resp.app()).isEqualTo("frameflow-select");
        assertThat(resp.version()).isEqualTo("0.1.0-SNAPSHOT");
    }

    @Test
    void ping_time_is_deterministic_under_fixed_clock() {
        PingResponse resp = service.buildPing();

        // 注意这里为什么用 Instant.parse(...) 再比较，而不是直接比较字符串：
        // OffsetDateTime.toString() 会按 JDK 规则省略末尾的 0（如秒为 0 时
        // 输出 "12:00Z" 而非 "12:00:00Z"），直接比字符串会踩格式陷阱。
        // 解析回时间再比语义，才是稳的断言写法。
        assertThat(Instant.parse(resp.serverTime()))
                .isEqualTo(Instant.parse("2026-08-22T12:00:30Z"));
    }
}
