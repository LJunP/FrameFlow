package com.frameflow.learning.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 平台级运维接口（/api/v1/admin/**）的共享密钥配置。
 *
 * ★ 核心：默认值故意留空，且 {@link AdminAuthFilter} 在留空时拒绝全部请求。
 * 这是 fail-closed：忘配密钥 = 运维接口全关，而不是"退化成谁都能用"。
 * 反例是 {@code frameflow.worker.result-key} 的做法——它有源码内默认值，
 * 一旦部署漏配就等于把内部接口对公网敞开（见 SecurityStartupValidator）。
 */
@ConfigurationProperties(prefix = "frameflow.admin")
public record AdminProperties(
        /** 平台管理员密钥（生产从环境变量注入；留空则 /api/v1/admin/** 全部拒绝）。 */
        @DefaultValue("") String apiKey) {
}
