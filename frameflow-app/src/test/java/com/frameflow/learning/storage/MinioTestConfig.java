package com.frameflow.learning.storage;

import com.frameflow.learning.product.storage.S3StorageAdapter;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.product.storage.StorageProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * 测试存储：Testcontainers 起真实 MinIO。
 *
 * ★ 核心：为什么不用 Mock 桩掉 StoragePort——presigned URL 的签名、
 * path-style 路由、ETag 引号、分片合并语义全都长在真实 S3 实现里，
 * Mock 出来的"URL 字符串"验证不了"客户端真的能直传成功"。
 * 集成测试用真实容器，才能证明端到端可用。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MinioTestConfig {

    @Bean(destroyMethod = "stop")
    MinIOContainer minio() {
        return new MinIOContainer(DockerImageName.parse("minio/minio:latest"));
    }

    /**
     * 覆盖主配置的 StoragePort，指向容器暴露的端点。
     * Bean 名故意不同于主配置的 storagePort——同名 Bean 会直接冲突报错
     *（@Primary 解决"两个候选谁优先"，解决不了"重名注册"）。
     */
    @Bean
    @Primary
    StoragePort minioTestStoragePort(MinIOContainer minio) {
        StorageProperties props = new StorageProperties(
                minio.getS3URL(), "us-east-1",
                minio.getUserName(), minio.getPassword(),
                "frameflow-test-media",
                Duration.ofMinutes(30),
                org.springframework.util.unit.DataSize.ofMegabytes(4),   // 阈值压到 4MB，让分片路径可测
                org.springframework.util.unit.DataSize.ofMegabytes(5),
                Duration.ofHours(1));
        return new S3StorageAdapter(props);
    }
}
