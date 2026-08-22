package com.frameflow.learning.product.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储装配：业务只依赖 StoragePort，这里决定真正的实现。
 * 测试环境可用 @Primary 覆盖成指向 MinIO 容器的实例（见 MinioTestConfig）。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public StoragePort storagePort(StorageProperties props) {
        return new S3StorageAdapter(props);
    }
}
