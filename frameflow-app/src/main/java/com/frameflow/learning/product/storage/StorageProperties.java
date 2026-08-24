package com.frameflow.learning.product.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 对象存储配置（application.yml 的 frameflow.storage 段）。
 * record 构造器绑定 + @DefaultValue：没配的项有安全默认值。
 * 大小用 DataSize（支持 "32MB" 可读写法）、时长用 Duration（"PT30M"）——
 * 绑定 long 是绑不了 "32MB" 的（NumberFormatException）。
 */
@ConfigurationProperties(prefix = "frameflow.storage")
public record StorageProperties(
        String endpoint,
        @DefaultValue("") String publicEndpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("frameflow-media") String bucket,
        @DefaultValue("PT30M") Duration presignTtl,
        @DefaultValue("32MB") DataSize multipartThreshold,
        @DefaultValue("8MB") DataSize partSize,
        @DefaultValue("PT1H") Duration uploadSessionTtl) {
}
