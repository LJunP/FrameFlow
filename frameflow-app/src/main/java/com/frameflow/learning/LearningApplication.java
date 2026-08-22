package com.frameflow.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FrameFlow Select 后端入口。
 *
 * 【F1 阅读起点】从这里开始：main → 本类两个注解 → config/AppInfoProperties
 * → ping/PingController → ping/PingService → resources/application.yml。
 */
// ★ 核心：@ConfigurationPropertiesScan 扫描并注册带 @ConfigurationProperties
// 的类（如 AppInfoProperties）。缺了它，配置类不会成为 Bean，注入处启动即失败
//（NoSuchBeanDefinitionException）——"配置类写了却忘了启用"是最常见的新手故障，
// 症状是启动报错找不到 Bean，而不是配置值为 null，注意与 prefix 写错区分。
@SpringBootApplication
@ConfigurationPropertiesScan
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
