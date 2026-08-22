package com.frameflow.learning;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 测试数据库：Testcontainers 用 Docker 起一个真实 PostgreSQL 16。
 *
 * ★ 核心：为什么不用 H2 内存库"假装"是 PostgreSQL——方言差异
 * （RETURNING、ON CONFLICT、TIMESTAMPTZ 行为……）会让"测试全绿、上线就炸"。
 * Testcontainers 起的是和生产同版本的真数据库，测过的 SQL 就是会生效的 SQL。
 *
 * @ServiceConnection 让 Spring Boot 自动把容器的主机/端口/库名接到
 * spring.datasource 上，业务配置一行都不用改。
 * Spring 的测试上下文缓存机制：所有引用本配置的测试类共享同一个容器，
 * 不会每个测试类都启停一次数据库。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
