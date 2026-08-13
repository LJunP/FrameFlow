# 验收矩阵

| 领域 | 关键验收 | 阶段 |
|---|---|---|
| 构建 | `mvn clean verify` 通过，依赖和迁移可验证 | P0 |
| 身份与权限 | 未登录、跨团队、团队角色越权均被拒绝；只能发现自己 ACTIVE 的团队关系；CLIENT 不进入团队角色；任一 ACTIVE 用户可创建团队并自动成为 OWNER | M01 |
| Token/会话 | RS256 的 alg/kid/iss/aud/exp 全校验；Refresh 只存 SHA-256；轮换、重放撤销 family、无 Access Token 登出均通过 | M01 |
| 团队成员 | 仅 OWNER 可直接添加已注册用户为 ACTIVE、改角色或移除；重复 ACTIVE 成员、最后 OWNER、自移除返回稳定 409 错误码；M01 不产生 INVITED | M01 |
| Identity 请求幂等 | PostgreSQL `idempotency_records` 在无 Redis 时保证两个声明端点并发只生效一次、结果重放、payload 冲突与处理中语义 | M01 |
| API 契约 | 所有响应含 `X-Request-Id`；错误 body 的 requestId 同值；运行时 OpenAPI 与手写契约无未批准差异 | M01 |
| 项目/Brief | 已交付不可删；仅一个当前 Brief；事务回滚；CLIENT 只能访问明确授权的项目内容 | M2 |
| 任务 | 旧 version 更新返回 409；状态机非法转换拒绝 | M3 |
| 素材 | 上传校验、版本不可覆盖、权限下载、对象/DB 补偿 | M4 |
| 模块边界 | ArchUnit 阻止跨模块 Repository/Mapper/Entity 依赖 | M01～M4 |
| Redis | 缓存命中/失效/TTL/降级；PostgreSQL 幂等加速与 Redis 故障回源结果一致；限流 429 | M5 |
| RabbitMQ | Confirm、ACK/NACK、重试、DLQ、幂等、人工重放 | M6 |
| AI | suggestion 不自动写正式数据；超时/结构错误可控 | M7 |
| 审核/交付 | 锁定版本、重复确认幂等、授权过期禁止交付、审计 | M8 |
| DDD | 聚合不变量和纯领域测试可解释 | M9 |
| Outbox/Kafka | 事务写入、重复消费、DLT、回放、schema 兼容 | M11 |
| 性能/JVM | 固定环境压测；JFR/jstack/GC 故障实验 | M12 |
| 服务边界 | 独立库/迁移、无共享业务表、API/事件契约 | M13 |
| 服务治理 | Gateway 路由、Feign 超时、有限重试、熔断、降级、Trace | M14～M15 |
| 日志分析 | JSON 日志可按 service/traceId/error 在 Kibana 检索 | M15 |
| Kubernetes | Probe、requests/limits、滚动发布、rollout undo、排障 | M16 |
| HPA | 指标和扩缩容实验，或诚实记录本地环境限制 | M16 |
| Istio | 90/10 或 Header 灰度、mTLS、AuthorizationPolicy、回滚 | M16-G |
| 证据 | 每项有命令、原始输出、commit、环境和限制 | 全阶段 |
