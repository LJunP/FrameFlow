# Prompt 02：工程强化、事件、压测与 JVM

在 MVP 可运行且基础设施集成测试稳定后执行 M9～M12。

## 目标

- 用 DDD 深化 Asset/License/Delivery 一个复杂子域；
- M11 以 Outbox + Kafka 发布已发生领域事件；RabbitMQ 继续负责待执行任务；
- 使用 Testcontainers、契约测试、k6、JFR、jcmd、jstack 和 GC 日志；
- 形成真实的性能基线、故障实验和复盘。

## 必须

- 事件包含 eventId、eventType、schemaVersion、occurredAt、producer；消费者幂等、重试、DLT、回放。
- 压测固定环境、数据量、并发和持续时间，保存原始结果；不得将本地数字写成生产容量。
- 先定位 SQL、索引、连接池、缓存、消费模型、线程池，再考虑堆和 GC 参数。
- 至少完成线程池堆积、受控内存/OOM、死锁/阻塞三个隔离实验。
- 每项实验保存采集命令、原始输出、根因、修复、复测和限制。
