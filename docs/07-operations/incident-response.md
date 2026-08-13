# 故障响应

## 1. 统一格式

```text
Incident ID
现象与影响
发生时间与环境
第一批命令
日志/Trace/指标证据
假设与验证
根因
临时止血
恢复/回滚
永久修复
回归测试
预防措施
限制条件
```

## 2. 优先演练矩阵

| 故障 | 主要证据 | 止血/恢复 |
|---|---|---|
| PostgreSQL 不可用 | readiness、连接池、数据库日志 | 保持不就绪/恢复依赖，禁止假成功 |
| Redis 不可用 | 命中率、错误和回源耗时 | Cache-Aside 回源；限流/幂等降级 |
| RabbitMQ 堆积 | queue depth、消费速率、失败数 | 限制入口、扩消费者、排查毒消息 |
| Kafka Lag | consumer lag、DLT、事件错误 | 暂停非关键消费者、修复后重放 |
| AI Provider 超时 | task 状态、耗时、重试次数 | 有限重试、标记待处理、避免重试风暴 |
| Gateway 路由错误 | 访问日志、路由配置、Trace | 回滚配置/路由 |
| 下游服务超时 | Trace span、Resilience4j 指标 | 熔断、降级、回滚版本 |
| Pod Pending | events、资源和调度 | 调整资源/节点或撤销发布 |
| CrashLoopBackOff | 当前/previous 日志 | 修配置或 rollout undo |
| OOMKilled | limits、GC/JFR、堆使用 | 限制并发、回滚、定位内存 |
| readiness 失败 | 探针响应、依赖状态 | 修复依赖或保持摘流 |
| Istio 503/mTLS 失败 | Envoy 日志、proxy-config、策略 | 切回 v1、修配置、回归验证 |

## 3. 原则

- 先保护数据一致性和权限，再追求恢复速度。
- 不用无限重试、不返回伪成功、不绕过审计。
- 每次故障演练必须保存原始输出，并区分本地实验与生产经验。
