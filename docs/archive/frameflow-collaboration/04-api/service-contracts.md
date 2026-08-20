# 服务契约

## 1. 契约类型

| 契约 | 适用 | 版本方式 | 验证 |
|---|---|---|---|
| 外部 REST | Client/Gateway 到服务 | `/api/v1` | OpenAPI + API 测试 |
| 内部 REST | 服务间同步查询/命令 | 内部 header + API 版本 | Spring Cloud Contract/Pact 二选一 |
| 领域事件 | Kafka 消费者 | `schemaVersion` | Schema/消费者契约测试 |
| RabbitMQ 任务 | Worker 执行命令 | task payload version | 消费者集成测试 |

## 2. 内部调用要求

- 内部请求携带 `X-Request-Id`、`traceparent`，不得传播不必要的用户敏感 Token。
- 服务必须在本地重新做认证、资源归属和参数校验，不能只信任 Gateway。
- 每次调用设置明确连接超时、读取超时和整体超时。
- 重试只用于幂等或可安全去重的请求，必须有次数和预算。
- 下游 4xx 不重试；超时/部分 5xx 需按操作幂等性决定。
- 熔断和降级响应不得掩盖写入失败或返回伪造成功。

## 3. 服务资源（定稿）

| 服务 | 外部资源 | 内部接口示例 |
|---|---|---|
| Identity | `/auth/*`、`/teams/*` | 成员角色查询、Token 校验 |
| Project | `/projects/*`、`/briefs/*`、`/tasks/*` | 项目归属校验、Brief 查询、任务状态 |
| Asset-Workflow | `/assets/*`、`/reviews/*`、`/delivery/*`、`/ai-tasks/*` | 版本状态、审核、授权校验、交付锁定 |
| Gateway | `/api/v1/**` | 路由、入口限流和 requestId |

## 4. 兼容性

新增字段必须可选；删除或修改语义必须先废弃；错误响应必须保留 `code`、`message`、`requestId`、`traceId`；不得向客户端返回服务名、堆栈或数据库错误。
