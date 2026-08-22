package com.frameflow.learning;

import com.frameflow.learning.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文冒烟测试：整个应用能启动 = 依赖注入、数据源、Flyway 迁移全部就绪。
 * 现在 classpath 上有数据库依赖，Flyway 会在启动时跑迁移——所以这里
 * 也必须 @Import Testcontainers 配置，起一个真实 PostgreSQL。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LearningApplicationTests {

    @Test
    void contextLoads() {
        assertThat(LearningApplication.class).isNotNull();
    }
}
