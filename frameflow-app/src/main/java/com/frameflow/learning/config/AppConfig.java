package com.frameflow.learning.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级通用 Bean 定义（F1 阶段只有 Clock）。
 */
@Configuration
public class AppConfig {

    // ★ 核心：把 Clock 声明为 Bean，是"生产/测试双态"的标准手法——
    // 生产环境注入真实系统时钟；需要确定性时间的测试用 @TestConfiguration
    // 覆盖成 Clock.fixed(...)，业务代码一行不改。
    // 改坏后果：删掉这个 Bean，任何注入 Clock 的组件启动即失败
    //（NoSuchBeanDefinitionException）。
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
