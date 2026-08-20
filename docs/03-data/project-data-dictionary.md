# 项目与 Brief 数据字典（M02 Contract Gate 草案）

> 状态：**DRAFT / NOT APPROVED**。本文只是 `FF-M02-001` Contract Gate 的审批输入，不是权威数据契约，不允许据此派发 M02 代码。本次不修改 `docs/04-api/openapi/frameflow-v1.yaml`。

## 1. 事实、已批准边界与 UNKNOWN

### FACT（已验证）

- `FF-M01-001=DONE`，`FF-M01H-001=IN_PROGRESS`；M02 业务代码尚未派发。
- 当前权威 OpenAPI 只覆盖 M01 身份/团队端点，尚无可供 M02 实现的项目/Brief 完整契约。
- 当前工作树保留 `V3__table_and_column_comments.sql`；本任务不修改或删除它。若 V3 最终保留，M02 新迁移必须从 **V4** 开始，不得复用 V3。
- MVP 唯一事务主库是 PostgreSQL；`project` 模块拥有 client/project/project-member/brief 数据边界。

### DECISION（已批准边界）

- 项目属于一个 Team，所有项目资源必须可追溯到 `team_id`。
- `CLIENT` 是项目级外部角色，不进入 `team_members.role`。
- 一个项目同时最多一个 current Brief；切换 current 必须在一个本地事务中完成。
- `governance` 模块是 MVP `audit_logs` 和基础站内 `notifications` 的唯一表所有者；`project` 只经 AuditPort/NotificationPort 或应用事件协作。
- Redis 不是 M02 依赖；PostgreSQL 唯一约束与事务是并发正确性的事实源。

### UNKNOWN（必须获得用户批准）

1. `clients` 是团队内部客户资料，还是必须关联 `users` 的外部账号；匿名联系人是否允许。
2. Team OWNER/PRODUCER 是否对所有团队项目拥有隐式访问，以及 `PROJECT_MANAGER / CONTRIBUTOR / VIEWER / CLIENT` 的精确权限矩阵。
3. 项目与 Brief 的 endpoint、request/response schema、分页/过滤、幂等头、错误码与 401/403/404 语义。
4. 项目状态转换的命令形式、乐观锁字段与 `DELIVERED/ARCHIVED` 后可修改范围。
5. Brief 内容是版本化 JSONB 快照还是结构化列；“新建版本 + 切换 current”的并发冲突语义。
6. `X-Correlation-Id` 的请求/响应契约、字符集、最大长度、生成方与信任边界。`X-Request-Id` 仍由后端为每次 HTTP attempt 生成。
7. 哪些项目/Brief 变更必须同事务写审计，哪些事件产生基础站内通知，以及通知失败的重试边界。
8. 项目删除/归档、客户脱敏/保留期与审计数据保留期。

## 2. 候选表（PROPOSAL，非定稿）

### 2.1 clients

| 列 | 候选类型 | nullable | 候选约束/说明 |
|---|---|---|---|
| id | BIGSERIAL | no | PK |
| team_id | BIGINT | no | 归属 Team；跨团队不可引用 |
| name | VARCHAR(160) | no | 客户展示名 |
| contact_user_id | BIGINT | yes | 是否必须关联 users 仍为 UNKNOWN |
| contact_email | VARCHAR(255) | yes | 个人数据，需脱敏/保留决策 |
| status | VARCHAR(20) | no | 候选 ACTIVE/ARCHIVED，尚未批准 |
| created_by / created_at / updated_at | BIGINT / TIMESTAMPTZ | no | 审计元数据 |

### 2.2 projects

| 列 | 候选类型 | nullable | 候选约束/说明 |
|---|---|---|---|
| id | BIGSERIAL | no | PK |
| team_id | BIGINT | no | 所有资源隔离根 |
| client_id | BIGINT | yes | 必须属于同一 team |
| name | VARCHAR(200) | no | 展示名 |
| status | VARCHAR(24) | no | PRD 状态集；命令/转换契约待批准 |
| version | BIGINT | no | 乐观锁，初始值待批准 |
| created_by / created_at / updated_at | BIGINT / TIMESTAMPTZ | no | 审计元数据 |

候选索引：`idx_projects_team_status(team_id, status)`、`idx_projects_client(client_id)`。是否允许同团队重名项目尚未决定。

### 2.3 project_members

| 列 | 候选类型 | nullable | 候选约束/说明 |
|---|---|---|---|
| id | BIGSERIAL | no | PK |
| project_id | BIGINT | no | FK projects.id |
| user_id | BIGINT | no | 指向 Identity user ID；单体中是否建物理 FK 待决定 |
| role | VARCHAR(24) | no | PROJECT_MANAGER / CONTRIBUTOR / VIEWER / CLIENT |
| status | VARCHAR(20) | no | 候选 ACTIVE/REMOVED；邀请流程不得暗中引入 |
| version | BIGINT | no | 乐观锁 |
| created_by / created_at / updated_at | BIGINT / TIMESTAMPTZ | no | 审计元数据 |

候选约束：`UNIQUE(project_id, user_id)`。重新激活是复用 REMOVED 行还是新建历史行尚未批准。

### 2.4 brief_versions

| 列 | 候选类型 | nullable | 候选约束/说明 |
|---|---|---|---|
| id | BIGSERIAL | no | PK |
| project_id | BIGINT | no | FK projects.id |
| version_no | INTEGER | no | 候选 `UNIQUE(project_id, version_no)` |
| content | JSONB | no | 内容 schema 尚未批准 |
| is_current | BOOLEAN | no | 一个项目最多一行 true |
| change_summary | VARCHAR(500) | yes | 版本说明 |
| created_by / created_at | BIGINT / TIMESTAMPTZ | no | Brief 版本候选为不可变快照，不提供 updated_at |

候选数据库防线：

```sql
CREATE UNIQUE INDEX uq_brief_versions_one_current
ON brief_versions(project_id)
WHERE is_current = TRUE;
```

该 SQL 仅是 PROPOSAL；必须同时批准应用事务、锁策略、冲突错误码和并发测试，不能只靠索引宣称 Contract Gate 通过。

## 3. 待批准的 API/权限/测试输出

Contract Gate 至少必须生成并获用户批准：

1. endpoint/operationId/request/response/error/header 表；
2. TeamRole × ProjectRole × 资源操作权限矩阵；
3. 创建项目、成员变更、Brief 版本创建/切换的事务与幂等策略；
4. 401/403/404/409 与并发冲突错误码；
5. 审计事件、基础站内通知触发器及所有者映射；
6. Requirement → Acceptance → Test → Evidence 的完整映射；
7. 新迁移版本号复核：V3 若保留，则从 V4 开始。

## 4. Gate 结论

**UNKNOWN / NOT APPROVED**：上述决策尚未闭环，`FF-M02-001` 必须保持 DRAFT，`FF-M02-002`～`004` 必须保持 NOT_READY。本草案不证明 M02 Contract Gate 已通过。
