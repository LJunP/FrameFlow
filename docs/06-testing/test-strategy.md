# 测试策略

## 1. 测试分层

| 层级 | 目标 | 主要工具/证据 |
|---|---|---|
| Domain | 不变量、状态机、授权/锁定规则 | JUnit 5、纯 Java 测试 |
| Architecture | 模块依赖、禁止跨 Repository/Mapper | ArchUnit |
| Service | 权限、事务、幂等、错误分类 | JUnit、Mockito、PostgreSQL |
| API | 认证、校验、越权、409/429、错误映射 | MockMvc、OpenAPI |
| Integration | PostgreSQL、Redis、MinIO、RabbitMQ | Testcontainers |
| Contract | 服务 API、事件 schema、兼容窗口 | Spring Cloud Contract 或 Pact（二选一） |
| E2E | 上传 → 分析 → 审核 → 交付 | Compose/Testcontainers |
| Performance | P50/P95/P99、错误率、资源、缓存命中 | k6、Actuator、Prometheus |
| JVM/Fault | 队列堆积、阻塞、OOM、依赖故障、回滚 | JFR、jcmd、jstack、GC 日志、Runbook |
| Platform | Probe、rollout/rollback、HPA、Istio 灰度/mTLS | kubectl、helm、istioctl |

## 2. 强制行为

- Mock 不替代关键基础设施集成测试。
- 每个关键业务规则至少有成功、边界和失败测试。
- 每个外部依赖必须有不可用或超时测试，验证不会返回伪成功。
- 并发规则必须有真实并发测试：旧 version 冲突、幂等键重复、重复消费。
- 测试必须提供真实命令、运行环境、原始结果和限制说明。

## 3. 阶段要求

- P0：构建、健康检查、迁移和基础 API 测试。
- M01～M04：权限、事务、状态机、乐观锁、MinIO/上传测试。
- M5～M8：Redis 降级、RabbitMQ 重试/DLQ/幂等、AI suggestion、E2E。
- M9～M12：DDD、Outbox/Kafka、契约、压测和 JVM 故障。
- M13～M15：服务间契约、超时/熔断/降级、跨服务 Trace。
- M16/M16-G：K8s 发布回滚、Probe、资源、Istio 流量和安全。
