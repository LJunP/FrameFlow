# FrameFlow 详细设计说明书

> 版本：0.2（求职版数据与一致性基线）  
> 本文定义模块化单体 MVP 的领域对象、数据约束、API 分组、对象存储、消息和微服务数据所有权边界。各阶段只迁移当期权威数据字典列出的表；M01 不迁移 M02 的 `project_members`。

## 1. 模块与领域对象

| 模块 | 主要对象 | 关键规则 | 数据所有权（微服务阶段，定稿） |
|---|---|---|---|
| Identity | User、Team、TeamMember、RefreshTokenSession、IdentityIdempotencyRecord | 后端鉴权、会话撤销、团队写请求幂等，不信任客户端角色 | `identity-service` / MySQL（工程实验轨道） |
| Project | Client、Project、BriefVersion | 一个项目只有一个当前 Brief | `project-service` / PostgreSQL |
| Workflow | ShotTask、TaskComment、Review | 乐观锁、角色和状态机 | 任务/评论归 `project-service`；审核归 `asset-workflow-service` |
| Asset | Asset、AssetVersion、License | 版本不可变、授权和引用可追溯 | `asset-workflow-service` / PostgreSQL |
| AI | AiTask、PromptTemplate、Suggestion | 输出仅建议，人工确认 | 业务事实归 `asset-workflow-service`；Worker 只拥有运行日志/job store |
| Delivery | DeliveryPackage、DeliveryItem | 固定版本、确认幂等、不可覆盖 | `asset-workflow-service` / PostgreSQL |
| Governance | AuditLog、OutboxEvent | 证据、审计、最终一致 | 事件消费者/所属服务；幂等记录归实际承载写操作的业务模块 |

## 2. MVP 数据模型

MVP 使用一个 PostgreSQL 实例，但必须按模块访问规则隔离 Mapper、Repository 和应用服务。模块共享实例不等于可以任意访问表。

```text
User --< TeamMember >-- Team --< Project --< BriefVersion
  └--< RefreshTokenSession
                                 ├--< ShotTask --< TaskComment
                                 ├--< Asset --< AssetVersion
                                 ├--< Review --< ReviewComment
                                 ├--< AiTask / Suggestion
                                 └--< DeliveryPackage --< DeliveryItem
```

### 最小表集合

```text
users
teams
team_members
refresh_token_sessions
clients
projects
brief_versions
shot_tasks
task_comments
assets
asset_versions
licenses
reviews
review_comments
ai_tasks
ai_call_logs
suggestions
delivery_packages
delivery_items
audit_logs
outbox_events
idempotency_records
```

所有核心表必须有：

```text
id
created_at
updated_at
created_by（适用时）
version（有并发更新的实体）
```

所有项目资源必须能通过 `project_id → team_id` 追溯归属。`users`、`teams`、`team_members`、`refresh_token_sessions` 与 M01 团队端点的 `idempotency_records` 在 M13 拆出前属于单体 Identity 模块；`project_members` 从 M02 起属于 Project 模块。

## 3. 关键约束

```text
team_members: unique(team_id, user_id)
brief_versions: 单项目最多一个 current=true
asset_versions: unique(asset_id, version_no)
delivery_items: 固定 pinned_asset_version_id
idempotency_records: unique(scope, idempotency_key)
outbox_events: unique(event_id)
ai_tasks: provider/external_request_id 幂等约束（适用时）
```

数据库约束是最后一道防线，业务状态机、权限和幂等仍必须在 Service 层验证。

## 4. 状态、并发与事务

- 项目、任务、审核、交付严格按 PRD 状态机流转。
- 状态变更使用 `version` 乐观锁；旧版本更新返回 `409 Conflict`。
- 创建新 Brief 并切换 current、确认交付并锁定版本等使用 Service 事务边界。
- 文件上传与数据库写入不可假设原子性；失败必须清理、补偿或记录待处理状态。
- RabbitMQ/Kafka 消费者必须幂等；外部回调必须按业务键去重。
- MVP 的 `outbox_events` 可先作为预留表；只有 M11 启用 Outbox Publisher 后，才宣称 Kafka 可靠事件链路已运行。

## 5. API 规范分组

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

写接口规则：

```text
只有 OpenAPI 显式声明的高风险写操作必须携带 Idempotency-Key
并发更新携带 version 或 If-Match
统一错误码
所有响应携带 X-Request-Id；错误 body 的 requestId 与 header 同值，traceId 可选
权限在后端校验
```

微服务阶段另外区分外部 Gateway API、服务内部 API 和领域事件，详见 `docs/04-api/`。

## 6. 文件与 MinIO

```text
客户端申请上传会话
→ 服务端校验团队/项目权限
→ 返回短期预签名上传 URL
→ 客户端直传 MinIO
→ 客户端确认
→ 创建 AssetVersion
→ RabbitMQ 投递处理任务
```

对象路径：

```text
team/{teamId}/project/{projectId}/raw/{assetId}/{versionId}
team/{teamId}/project/{projectId}/preview/{assetId}/{versionId}
team/{teamId}/project/{projectId}/delivery/{packageId}/{versionNo}
```

不能把原始客户端文件名直接作为对象完整路径。对象和数据库失败时必须有清理、补偿或孤儿对象扫描方案。

## 7. RabbitMQ 与 AI

M6 使用 Fake/Stub 分析器建立可靠任务闭环；M7 才接入真实可替换 Provider。

```text
AssetVersion created
→ AiTask created
→ analysis.requested
→ Consumer（手动 ACK）
→ Suggestion / task status update
→ transient failure retry
→ permanent failure DLQ
```

必须有 Confirm、重试、DLQ、幂等、人工重放、taskId/eventId 安全日志。AI 输出不能直接改变正式素材、任务或交付状态。

## 8. Redis

建议 Key：

```text
frameflow:project:detail:{projectId}
frameflow:team:member-role:{teamId}:{userId}
frameflow:idempotency:{scope}:{key}
frameflow:ratelimit:{scope}:{subject}:{window}
```

规则：Cache-Aside；数据库是事实来源；写成功后失效；TTL 防止永久脏数据；Redis 不可用时安全回源；不滥用分布式锁。M01 已由 PostgreSQL `idempotency_records` 提供请求幂等事实，M05 的 Redis key 只作加速；缓存丢失不得改变去重结果。

## 9. Outbox 与 Kafka（M11 后）

```text
业务事务：保存业务变化 + OutboxEvent
→ Publisher 发布 Kafka
→ 消费者按 eventId 去重
→ 成功后提交 offset
→ 重试 Topic / DLT
```

RabbitMQ 继续执行任务；Kafka 分发已发生事件。事件必须包含 `eventId`、`eventType`、`schemaVersion`、`occurredAt`、`producer` 和脱敏业务标识。

## 10. 微服务数据所有权（定稿）

| 服务 | 负责数据 | 禁止直接读取 | 对外提供 |
|---|---|---|---|
| identity-service | users、teams、team_members、refresh_token_sessions、Identity 端点的 idempotency_records | projects、project_members、assets、delivery | 身份、成员、会话和权限 API |
| project-service | clients、projects、project_members、brief_versions、shot_tasks、task_comments | assets、reviews、delivery 物理表 | 项目、Brief、任务 API |
| asset-workflow-service | assets、asset_versions、licenses、reviews、review_comments、delivery | identity/project 物理表 | 素材、审核、授权和交付 API |
| AI Worker | 运行日志/job store；不拥有 ai_tasks/suggestions 业务事实 | 直接写其他服务业务表 | 任务结果事件/回调 |

AI Worker 协作模型（定稿）：业务服务拥有 `ai_tasks` 与 `suggestions` → Worker 经 RabbitMQ 命令消息接收任务并执行 Provider 调用 → 结果事件回传 → 业务服务幂等更新。

跨服务需要通过版本化 API、领域事件或明确的读模型协作。禁止共享业务表、跨服务 Repository、跨库外键和 MySQL/PostgreSQL 双写。每个服务拥有独立 Flyway 迁移目录。

## 11. 测试最小要求

| 层级 | 最小验证 |
|---|---|
| Domain | 授权过期、锁定版本、状态规则 |
| Architecture | 模块依赖、禁止跨模块 Repository/Mapper |
| Service | 权限、事务、幂等、错误分类 |
| API | 认证、越权、校验、409 冲突、错误映射 |
| Integration | PostgreSQL、Redis、MinIO、RabbitMQ；Kafka 引入后再覆盖 |
| Contract | 服务 API、事件 schema、兼容字段和错误语义 |
| E2E | 上传→分析→审核→交付 |
| Platform | Kubernetes Probe、rollout/rollback、Istio 灰度/mTLS（进入阶段后） |

## 12. 待确认项

- 真实 AI Provider、回调签名与成本字段；
- 评论锚点是否含区域；
- 文件预览与转码标准；
- 客户外部访问链接、水印、下载限制；
- 前端框架与 OpenAPI 代码生成方式；
- 多租户、数据保留和删除策略；
- Trace 后端最终选择 Jaeger 或 Tempo。
