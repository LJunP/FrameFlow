# API 规范

## 1. 外部 API

- 使用 `/api/v1` 作为首期版本前缀；破坏性变更升级版本。
- 资源使用复数名词；状态变更使用明确动作子资源。
- OpenAPI 显式声明 `Idempotency-Key` 的高风险写操作必须携带该请求头；未声明的端点不要求客户端发送。并发更新使用 `version` 或 `If-Match`。
- 所有 HTTP 响应携带 `X-Request-Id` header；错误响应 body 同时包含值相同的 `requestId`，可观测环境可附带 `traceId`；错误使用统一错误模型。
- 分页默认使用 cursor，早期可兼容 page/size；排序字段必须白名单。
- MVP 由应用 Security Filter 校验身份并由业务模块校验角色/资源归属；微服务阶段再由 Gateway 与业务服务分别执行，任何阶段都不信任客户端角色或资源归属。
- 不在响应、日志或错误中泄露 Secret、Token、内部路径或堆栈。

## 2. 微服务 API

- Gateway 暴露外部 `/api/v1/**`；服务内部 API 不绕过 Gateway 对外发布。
- 内部调用必须携带 `X-Request-Id` 和 W3C `traceparent`，但只传播必要的身份信息。
- 内部服务必须有连接超时、读取超时和整体超时；禁止无限重试。
- 只有幂等或可安全去重的请求才允许重试；下游 4xx 默认不重试。
- 服务间认证、资源归属和参数校验不能只依赖 Gateway。
- 需要异步处理的请求返回任务标识和状态查询入口，不阻塞 HTTP 等待耗时任务。

## 3. API 兼容

- 新增字段应为可选；删除或修改语义必须经过弃用窗口。
- API 契约变化同步更新 OpenAPI、服务契约、测试和证据索引。
- `X-Request-Id` 是外部响应契约；入口应用为每次外部 HTTP 尝试生成新的 UUID，并在错误 body 中保持同值。内部调用继续通过 `X-Request-Id` 传播该值。
- 外部错误不暴露内部服务名、数据库错误、堆栈或重试实现细节。
- Gateway 将下游超时、熔断和不可用映射为稳定的外部错误码，保留 requestId/traceId。

## 4. 资源分组

```text
/auth/*
/teams/*
/clients/*
/projects/*
/projects/{projectId}/briefs
/projects/{projectId}/tasks
/projects/{projectId}/assets
/projects/{projectId}/reviews
/projects/{projectId}/ai-tasks
/projects/{projectId}/delivery-packages
/uploads/*
```
