package com.frameflow.learning.identity.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 令牌时效配置（application.yml 的 frameflow.security 段）。
 *
 * access token 短时效（15 分钟）+ refresh token 长时效（14 天）是标准组合：
 * 短命牌限制"令牌被盗"的窗口期，长命牌负责免频繁登录。
 */
@ConfigurationProperties(prefix = "frameflow.security")
public record SecurityProperties(Duration accessTokenTtl, Duration refreshTokenTtl) {
}
