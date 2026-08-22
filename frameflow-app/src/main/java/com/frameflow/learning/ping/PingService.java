package com.frameflow.learning.ping;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.frameflow.learning.config.AppInfoProperties;

/**
 * 组装 ping 响应（F1-T1 业务逻辑刻意极简，重点展示"可测试的写法"）。
 */
@Service
public class PingService {

    private final AppInfoProperties appInfo;
    private final Clock clock;

    // ★ 核心：注入 Clock，而不是在方法里直接调 LocalDateTime.now()——
    // "当前时间"是不可控的外部输入，把它变成构造器依赖后，单元测试可以
    // 传入一个固定时钟，断言就能精确到字符串、永远不抖动。
    // 这一个动作就是"可测试设计"最典型的一课：把不可控的东西推出代码外。
    // 改坏后果：若改回 OffsetDateTime.now()，测试只能断言"大约是现在"，
    // 出现偶发失败时你永远无法复现。
    public PingService(AppInfoProperties appInfo, Clock clock) {
        this.appInfo = appInfo;
        this.clock = clock;
    }

    public PingResponse buildPing() {
        return new PingResponse(
                appInfo.name(),
                appInfo.version(),
                OffsetDateTime.now(clock).toString());
    }
}
