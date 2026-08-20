# 服务边界与数据所有权（定稿）

> 状态：DECISION（已批准，2026-08-12 定稿）。本文件是服务归属的唯一权威；概要设计、详细设计、ADR-008、服务契约和拆分计划均以本文件为准。

## 1. 首批服务与数据所有权（定稿）

| 服务 | 数据库 | 负责数据（唯一所有者） | 不拥有 |
|---|---|---|---|
| `gateway-service` | 无业务库 | 路由与入口配置 | 任何业务表 |
| `identity-service` | MySQL（工程实验轨道） | `users`、`teams`、`team_members` | project/asset 表 |
| `project-service` | PostgreSQL | `clients`、`projects`、`project_members`、`brief_versions`、`shot_tasks`、`task_comments` | asset/review/delivery 表 |
| `asset-workflow-service` | PostgreSQL | `assets`、`asset_versions`、`licenses`、`reviews`、`review_comments`、`delivery_packages`、`delivery_items` | project/identity 表 |
| `ai-worker`（独立部署） | 运行日志/job store | Provider 调用执行、运行日志 | 业务表（`ai_tasks`、`suggestions` 由业务服务拥有） |

### 归属原则（定稿）

```text
项目计划、分镜任务、任务评论      → project-service
围绕素材版本的审核、批注、授权、交付 → asset-workflow-service
AI 任务与 suggestion 的业务事实   → 由发起分析的业务服务（asset-workflow-service）拥有
AI Worker 只执行 Provider 调用     → 拥有独立运行日志/job store，通过结果事件回传
```

## 2. AI Worker 协作模型（定稿）

```text
业务服务（asset-workflow-service）
  拥有 ai_tasks、suggestions 和业务可见状态
        │ RabbitMQ command（analyze.asset-version.v1）
        ▼
AI Worker
  执行 Provider 调用
  拥有独立运行日志 / job store
        │ RabbitMQ result（asset-analysis.completed.v1）
        ▼
业务服务
  幂等更新 ai_tasks 与 suggestion
```

- **回传通道定稿：RabbitMQ result queue**（M6/M7 主线；Kafka 领域事件只用于 M11 后的多消费者场景，不承担 M6/M7 结果回传）；
- Worker 不直接写业务表，不改变审核、交付、素材状态；
- 结果消息包含 `taskId`、`eventId`、`schemaVersion`、结构化结果和失败原因；幂等键为 `taskId`，失败进入重试/DLQ；
- 这样 Worker 可独立扩缩容且不会越界修改业务事实。

## 3. 所有权规则（含治理与辅助表）

| 表/对象 | 唯一所有者 |
|---|---|
| users、teams、team_members、refresh_token_sessions | identity-service |
| clients、projects、project_members、brief_versions、shot_tasks、task_comments | project-service |
| assets、asset_versions、licenses、reviews、review_comments、delivery_packages、delivery_items、upload_sessions、ai_tasks、suggestions、ai_call_logs、prompt_templates | asset-workflow-service |
| audit_logs | MVP 单体内由 `governance` 模块唯一拥有；M13 拆分前必须另立 ADR 决定“业务服务本地审计事实 + 中央投影”边界 |
| outbox_events、idempotency_records | 所属业务服务；M01 Identity 团队端点的幂等记录归 identity-service |
| notifications | MVP 由 `governance` 模块唯一拥有的基础站内通知；外发邮件/短信和独立通知服务后置 |
| AI Worker 运行日志、job store | ai-worker（非业务表） |

- 一个业务表只能有一个服务负责写入和迁移。
- 其他服务不能直接读取该服务数据库表或共享 Repository。
- 跨服务读取通过 API、事件同步的读模型或明确的批处理导出。
- 不做 MySQL/PostgreSQL 双写、跨库外键或跨数据库事务。
- 每个服务拥有独立 Flyway 迁移目录和版本号。
- M13 之前 `governance` 不是独立服务，而是模块化单体的受控模块；业务模块只能经 AuditPort/NotificationPort 或应用事件协作。

## 4. 跨服务协作（定稿措辞）

| 场景 | 协作方式 | 一致性说明 |
|---|---|---|
| 团队身份/TeamMember 查询 | Identity API | 请求内校验 |
| 项目成员/ProjectMember 判定 | **project-service** 项目授权 API 或本地授权投影（不是 Identity） | 请求内校验或短期缓存 |
| 素材服务需要项目访问判定 | Project Authorization API / 授权投影 | 最终一致（投影有 TTL） |
| 创建素材 | **先远程授权/归属检查，再 Asset-Workflow 本地事务写入** | 不存在跨服务单一 ACID 事务；项目在校验后被归档/删除的竞态由项目版本、缓存失效和补偿处理 |
| 素材版本创建后分析 | RabbitMQ command（analyze.asset-version.v1） | 至少一次投递，消费者幂等 |
| 分析结果回传 | RabbitMQ result（asset-analysis.completed.v1）→ 业务服务幂等更新 | 最终一致，可重放 |
| 审核通过后基础站内通知 | 单体 NotificationPort / post-commit 应用事件；M11 后可换为 Outbox/Kafka | MVP 不依赖 Kafka；通知失败不得伪造业务回滚 |
| 交付确认 | Asset-Workflow 本地事务 | 业务幂等（永久唯一约束）、审计同事务 |

## 5. 迁移期间

1. 在单体中完成模块边界和访问规则；
2. 提取服务 API/事件契约；
3. 建立独立库和迁移脚本；
4. 复制必要数据并校验，不做持续双写；
5. 只读旁路 → 小流量切换 → 对比 → 保留单体回滚开关 → 切流 → 删除旧路径。

## 6. 禁止的"伪拆分"

- 多个服务共享同一组业务表并互相写入；
- 只复制包结构、没有独立契约和数据所有权；
- 用数据库触发器代替服务 API；
- 用无限重试掩盖下游超时；
- 没有回滚方案就切换全部流量。
