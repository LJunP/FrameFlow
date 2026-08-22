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

    // ★ 核心：密码哈希用 BCrypt——自带随机盐（同密码每次哈希结果不同，
    // 抗彩虹表）+ 刻意慢（暴力破解成本高）。盐由 encode 内部生成并编码进
    // 结果字符串，matches 时自动取出，代码不用手工管理盐。
    // 改坏后果：用 MD5/SHA-256 裸哈希，拖库后彩虹表秒破。
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
