# 幂等契约（Idempotency Contract）

> 状态：DECISION。适用于 OpenAPI 明确声明 `Idempotency-Key` 的写接口，以及后续阶段的交付确认、审核提交、AI 人工确认、素材版本创建等高风险写操作；消息消费使用独立的消息去重语义。

## 1. 语义

- 相同 `scope + idempotencyKey` 的写请求，无论重试多少次，业务效果只产生一次。
- 客户端必须为每个业务意图生成唯一键（UUID），不能复用于不同意图。
- 服务端返回结果对相同键保持一致：首次执行结果缓存返回，重复请求返回首次结果而非重新执行。

## 2. 请求头与作用域

| 字段 | 规则 |
|---|---|
| `Idempotency-Key` | 对契约中声明该请求头的操作必填；UUID 格式，最大 128 字符 |
| 作用域 | 按 `用户 + 操作类型 + 资源` 确定；键在作用域内唯一 |
| TTL | 键和结果缓存 24 小时；超期后允许新请求 |
| 并发 | 相同键并发到达时，只有一个执行业务事务；其余在有限等待后重放首次结果，等待超出请求预算时返回 `IDEMPOTENCY_IN_PROGRESS` |

M01 中声明该请求头的操作为 `POST /teams` 与 `POST /teams/{teamId}/members`。未在 OpenAPI 操作中声明该请求头的端点不要求客户端发送；新增受保护操作时，必须在 OpenAPI 中显式引用必填参数，不能仅依赖本文的概括性描述。

## 3. 幂等分层（三种不可混淆）

| 层 | 语义 | 实现 | 失效策略 |
|---|---|---|---|
| 请求去重（Request deduplication） | 同一 `scope+idempotencyKey` 的请求重试只执行一次 | M01 起使用 PostgreSQL `idempotency_records` 唯一约束、请求指纹和结果记录；M05 的 Redis 只作加速层 | 短期 TTL（24h），过期允许新请求 |
| 领域幂等（Domain idempotency） | 业务不变量永久成立，如"交付确认只发生一次"、"版本锁定不可覆盖" | 数据库唯一约束 + 状态机 | **永久**，不因请求键过期而失效 |
| 消息去重（Message deduplication） | 消费者对同一 `eventId`/`taskId` 只产生一次业务副作用 | 幂等表/唯一约束 | 按业务结果保留策略 |

请求键过期只影响"请求去重"层；领域幂等由业务状态和唯一约束兜底，二者必须同时实现。

### 3.1 M01 PostgreSQL 权威实现

- `idempotency_records` 由承载该操作的业务模块拥有；M01 的两条团队写操作由 Identity 模块拥有并迁移该表；
- `scope` 至少包含认证用户、HTTP 方法、操作和目标资源；`POST /teams` 使用用户级创建团队作用域，添加成员操作还必须包含 `teamId`；
- `request_hash` 是规范化方法、路径和 JSON body 的 SHA-256；相同键但哈希不同返回 `IDEMPOTENCY_CONFLICT`；
- 首次请求在同一 PostgreSQL 事务内占用唯一键、执行领域写入并记录 HTTP 状态和响应 JSON；事务回滚时幂等记录也回滚；
- 竞争请求依赖唯一约束和行锁串行化，首次事务提交后读取并重放业务响应；每次 HTTP 尝试生成新的 `X-Request-Id`，不重放旧请求的追踪标识；
- 过期记录可由定时清理或访问时清理；无论是否已物理清理，服务都必须按 `expires_at` 正确允许新的业务意图。

### 3.2 M05 Redis 加速边界

M05 可在 PostgreSQL 权威实现前增加 Redis 原子占位和结果缓存，以减少热点请求对数据库的压力；Redis 不是幂等事实源。Redis 未命中或不可用时必须安全回到 PostgreSQL 路径，不能绕过数据库唯一约束，也不能因为缓存丢失重复产生业务效果。

## 4. 其他要求

- 请求指纹：相同键但 payload 不同应返回 `IDEMPOTENCY_CONFLICT`（409）；
- 处理中状态：相同键并发到达时只有一个执行，其余有限等待；等待超出请求预算返回 `IDEMPOTENCY_IN_PROGRESS`（409），客户端只能使用同一键重试；
- 失败结果：失败的写请求不应缓存"成功"；重试使用同一键；
- 原子性：M01 的键记录、业务写入和成功结果记录必须在同一 PostgreSQL 事务；
- Redis 故障：M05 起安全回到 PostgreSQL 权威路径，不得伪造成功。

- RabbitMQ/Kafka 消费者必须按业务键（如 `eventId`、`taskId`）去重；
- 消费幂等不能依赖消息投递次数，必须由业务侧唯一约束或幂等表保证；
- 人工重放使用同一业务键，重放结果与首次一致。

## 5. 验收

```text
20 个相同 Idempotency-Key 并发写请求只生效一次
相同键重试返回首次业务结果，但使用本次请求的新 X-Request-Id
不同请求复用同一键返回 IDEMPOTENCY_CONFLICT
竞争等待超出请求预算返回 IDEMPOTENCY_IN_PROGRESS，使用同一键重试后得到首次结果
M01 在没有 Redis 时仅凭 PostgreSQL 通过以上验收
M05 Redis 不可用时回到 PostgreSQL 后仍通过以上验收
```
