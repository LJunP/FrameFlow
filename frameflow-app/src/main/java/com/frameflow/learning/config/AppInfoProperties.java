package com.frameflow.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * F1-T1 配置读取：把 application.yml 中 frameflow.app.* 段绑定成类型安全的对象。
 *
 * 阅读顺序：application.yml(frameflow.app 段) → 本类 → PingService → PingController。
 */
// ★ 核心：@ConfigurationProperties 把 YAML 配置集中绑定到一个 Java 类型——
// 相比把 @Value("${frameflow.app.name}") 散落在各个类里，这里集中定义、
// 类型安全（name 永远是 String，拼错配置名时启动即失败 fail-fast）。
// 改坏后果：若把 prefix 写错（如 "frameflow.product"），绑定不到任何配置，
// 字段会是 null——ping 接口返回 "app": null，且没有任何报错提示你配置丢了。
@ConfigurationProperties(prefix = "frameflow.app")
public record AppInfoProperties(String name, String version) {
}
