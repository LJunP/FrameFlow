package com.frameflow.learning;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 测试基础设施：Testcontainers 起真实中间件。
 *
 * ★ 核心：为什么不用 H2/内嵌替代品——方言与实现差异会让"测试全绿、
 * 上线就炸"。Testcontainers 起的是和生产同版本的真货，测过的 SQL/缓存/
 * 消息语义就是会生效的语义。
 *
 * Spring 的测试上下文缓存机制：所有引用本配置的测试类共享同一组容器。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    /**
     * F5：Redis 容器。GenericContainer 与专用容器类不同，Boot 无法从
     * 类型推断技术栈——必须显式 name="redis" 才会映射成
     * RedisConnectionDetails（否则启动报 ConnectionDetailsNotFoundException）。
     */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
