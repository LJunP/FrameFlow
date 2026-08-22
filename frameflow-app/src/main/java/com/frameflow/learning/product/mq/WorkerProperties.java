package com.frameflow.learning.product.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** worker 内部通信配置（frameflow.worker 段）。 */
@ConfigurationProperties(prefix = "frameflow.worker")
public record WorkerProperties(
        /** 内部回写接口的共享密钥（生产从环境变量注入）。 */
        @DefaultValue("frameflow-dev-worker-key") String resultKey) {
}
