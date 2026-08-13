# 错误码注册表

> 状态：DECISION（错误码稳定、可搜索、与具体服务实现解耦）。新增错误码必须先登记本表。

## 1. 响应结构

```json
{
  "code": "PROJECT_NOT_FOUND",
  "message": "项目不存在或无权访问",
  "requestId": "...",
  "traceId": "..."
}
```

所有 HTTP 响应必须携带 `X-Request-Id` header；错误响应 body 的 `requestId` 必须与该 header 完全相同。`traceId` 仅在可观测环境提供。

## 2. 错误码注册表

| code | HTTP | 用途 | 是否默认重试 |
|---|---|---|---|
| AUTH_REQUIRED | 401 | 未认证或 Token 缺失/无效 | 否 |
| TOKEN_EXPIRED | 401 | Token 过期 | 否 |
| FORBIDDEN | 403 | 已认证但角色/权限不足 | 否 |
| RESOURCE_NOT_FOUND | 404 | 资源不存在或按安全策略隐藏（无权访问统一 404） | 否 |
| PROJECT_NOT_FOUND | 404 | 项目不存在或无权访问（防枚举，与 RESOURCE_NOT_FOUND 同语义，供客户端区分域） | 否 |
| VALIDATION_FAILED | 400 | 参数校验失败 | 否 |
| VERSION_CONFLICT | 409 | 乐观锁冲突（version 过期） | 否，需重新读取 |
| STATE_CONFLICT | 409 | 状态机非法转换或重复状态 | 否 |
| IDEMPOTENCY_CONFLICT | 409 | 幂等键冲突（不同请求复用同一键） | 否 |
| IDEMPOTENCY_IN_PROGRESS | 409 | 相同幂等键的首次请求仍在处理且等待已超出本次请求预算 | 是，仅使用同一键有限重试 |
| USER_EMAIL_CONFLICT | 409 | 规范化邮箱已被注册 | 否 |
| TEAM_MEMBER_ALREADY_EXISTS | 409 | 目标用户已经是该团队的 ACTIVE 成员 | 否 |
| TEAM_LAST_OWNER_CONFLICT | 409 | 操作会使团队失去最后一名 ACTIVE OWNER | 否 |
| TEAM_SELF_REMOVAL_FORBIDDEN | 409 | OWNER 试图通过成员删除接口移除自己 | 否 |
| RATE_LIMITED | 429 | 触发限流 | 仅按 Retry-After 和幂等性 |
| INTERNAL_ERROR | 500 | 未预期错误（不返回内部细节） | 不承诺 |
| DEPENDENCY_UNAVAILABLE | 503 | 依赖不可用、熔断或受控降级 | 按预算和幂等性 |
| DEPENDENCY_TIMEOUT | 504 | 下游超时 | 按预算和幂等性 |
| BAD_GATEWAY | 502 | 下游返回无效响应 | 按预算和幂等性 |
| FILE_INVALID | 400 | 文件类型/大小/路径校验失败 | 否 |
| UPLOAD_SESSION_EXPIRED | 400 | 上传会话过期或已使用（固定 400，不使用 410） | 否 |

> 说明：异步任务受理使用 202 + 任务状态查询，属于成功语义，不在错误码注册表登记（错误码表只登记错误）。

## 3. 注册规则

- 错误码采用 `DOMAIN_REASON` 全大写下划线格式；
- 外部响应不得包含服务名、数据库错误、堆栈或内部路径；
- 内部日志和 Trace 保留完整关联（requestId/traceId/服务/耗时）；
- 新增或变更错误码必须同步更新本表、API 规范、契约测试和错误模型。

## 4. 下游错误映射

- 下游 4xx：透传稳定业务错误码，不重试；
- 下游超时/连接失败/部分 5xx：由 Resilience4j 处理，熔断打开时返回 `DEPENDENCY_UNAVAILABLE`；
- 降级不能伪造写入成功；写操作失败应返回可查询的待处理任务或明确失败。
