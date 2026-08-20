# 身份领域数据字典（M01 Ready Gate 输入）

> 状态：DECISION（M01 派发前必须与本文件一致）。本文只定义 M01 必须迁移的 Identity 表：`users`、`teams`、`team_members`、`refresh_token_sessions`、`idempotency_records`；项目成员表属于 M02/project 模块，不进入 M01 的 V2 迁移。

## 1. users

| 列 | 类型 | nullable | 默认 | 约束/说明 |
|---|---|---|---|---|
| id | BIGSERIAL | no | - | PK |
| email | VARCHAR(255) | no | - | UNIQUE；小写规范化 |
| password_hash | VARCHAR(100) | no | - | BCrypt，永不明文/不出现在日志 |
| display_name | VARCHAR(100) | no | - | - |
| status | VARCHAR(20) | no | 'ACTIVE' | ACTIVE / DISABLED / PENDING |
| last_login_at | TIMESTAMPTZ | yes | NULL | - |
| created_at | TIMESTAMPTZ | no | now() | - |
| updated_at | TIMESTAMPTZ | no | now() | - |

## 2. teams

| 列 | 类型 | nullable | 默认 | 约束/说明 |
|---|---|---|---|---|
| id | BIGSERIAL | no | - | PK |
| name | VARCHAR(100) | no | - | - |
| created_by | BIGINT | no | - | FK users.id |
| created_at | TIMESTAMPTZ | no | now() | - |
| updated_at | TIMESTAMPTZ | no | now() | - |

## 3. team_members

| 列 | 类型 | nullable | 默认 | 约束/说明 |
|---|---|---|---|---|
| id | BIGSERIAL | no | - | PK |
| team_id | BIGINT | no | - | FK teams.id |
| user_id | BIGINT | no | - | FK users.id |
| role | VARCHAR(20) | no | 'VIEWER' | OWNER / PRODUCER / EDITOR / VIEWER |
| status | VARCHAR(20) | no | 'ACTIVE' | ACTIVE / REMOVED；INVITED 等邀请态待未来完整邀请流程引入 |
| created_at | TIMESTAMPTZ | no | now() | - |
| updated_at | TIMESTAMPTZ | no | now() | - |

约束：`UNIQUE(team_id, user_id)`。创建团队与创建者 OWNER 成员关系必须在同一事务写入。**CLIENT 不是团队角色**（见权限矩阵）。

M01 成员生命周期与 OWNER 不变量：

- `POST /teams/{teamId}/members` 只直接添加已注册用户并产生 ACTIVE 成员，不创建 INVITED 记录；
- 已有 ACTIVE 关系再次添加返回 `TEAM_MEMBER_ALREADY_EXISTS`；已有 REMOVED 关系再次添加时复用原记录，更新角色并恢复为 ACTIVE；
- 每个团队始终至少保留一名 ACTIVE OWNER；降级或移除最后一名 ACTIVE OWNER 返回 `TEAM_LAST_OWNER_CONFLICT`；
- OWNER 不允许通过成员删除接口移除自己，返回 `TEAM_SELF_REMOVAL_FORBIDDEN`；这些计数与状态检查必须在锁定团队成员集合的事务中完成，不能只靠前端。

## 4. refresh_token_sessions

一行代表一个 Refresh Token 代次；`family_id` 将同一次设备登录及其后续轮换串成一个可整体撤销的会话。

| 列 | 类型 | nullable | 默认 | 约束/说明 |
|---|---|---|---|---|
| id | BIGSERIAL | no | - | PK |
| user_id | BIGINT | no | - | FK users.id |
| family_id | UUID | no | - | 由应用在登录时生成；同一设备轮换保持不变 |
| token_hash | CHAR(64) | no | - | UNIQUE；Refresh Token 的 SHA-256 小写十六进制哈希，禁止存明文 |
| status | VARCHAR(20) | no | 'ACTIVE' | ACTIVE / ROTATED / REVOKED |
| expires_at | TIMESTAMPTZ | no | - | 到期后即使 status=ACTIVE 也不得使用 |
| last_used_at | TIMESTAMPTZ | yes | NULL | 最近一次成功刷新时间 |
| rotated_at | TIMESTAMPTZ | yes | NULL | 被轮换时间 |
| revoked_at | TIMESTAMPTZ | yes | NULL | 被撤销时间 |
| replaced_by_id | BIGINT | yes | NULL | FK refresh_token_sessions.id；指向轮换后的 token 记录 |
| created_at | TIMESTAMPTZ | no | now() | - |
| updated_at | TIMESTAMPTZ | no | now() | - |

约束：`UNIQUE(token_hash)`；`replaced_by_id` 非空时必须指向同一 `user_id + family_id` 的记录。刷新流程必须在一个事务中锁定当前记录，插入新 ACTIVE 记录并将旧记录改为 ROTATED；重放 ROTATED/REVOKED token 时撤销该 `family_id` 下全部 ACTIVE 记录。登出只撤销当前设备 family，不影响其他设备。

## 5. idempotency_records

M01 的实例由 Identity 模块拥有，仅服务于 OpenAPI 已声明幂等头的 `POST /teams` 与 `POST /teams/{teamId}/members`。未来拆分后，各业务服务拥有自己的幂等记录和迁移，禁止共享此表作为跨服务事实源。

| 列 | 类型 | nullable | 默认 | 约束/说明 |
|---|---|---|---|---|
| id | BIGSERIAL | no | - | PK |
| scope | VARCHAR(255) | no | - | 认证用户 + HTTP 方法 + 操作 + 目标资源组成的稳定作用域 |
| idempotency_key | UUID | no | - | 客户端 `Idempotency-Key` |
| request_hash | CHAR(64) | no | - | 规范化方法、路径与 JSON body 的 SHA-256 小写十六进制 |
| status | VARCHAR(20) | no | 'PROCESSING' | PROCESSING / COMPLETED |
| response_status | INTEGER | yes | NULL | COMPLETED 时必填的 HTTP 状态码 |
| response_body | JSONB | yes | NULL | COMPLETED 时保存可重放的业务响应；禁止保存 Token、Secret 或追踪 ID |
| expires_at | TIMESTAMPTZ | no | - | 创建后 24 小时；过期后允许新业务意图 |
| created_at | TIMESTAMPTZ | no | now() | - |
| updated_at | TIMESTAMPTZ | no | now() | - |

约束：`UNIQUE(scope, idempotency_key)`。COMPLETED 记录必须同时具有 `response_status` 与 `response_body`；PROCESSING 记录不得伪造成功响应。首次请求的幂等记录、团队业务写入与 COMPLETED 结果必须在同一 PostgreSQL 事务提交，失败回滚时不得遗留“成功”记录。

## 6. 索引建议

```text
users: unique(email)
team_members: unique(team_id, user_id)；idx(user_id, status)；idx(team_id, role)
refresh_token_sessions: unique(token_hash)；idx(user_id, status)；idx(family_id, status)；idx(expires_at)
idempotency_records: unique(scope, idempotency_key)；idx(expires_at)；idx(status, updated_at)
```

## 7. 生命周期与安全

- 密码策略：BCrypt cost=10+；不允许明文存日志/响应/Token；
- Refresh Token：32 字节安全随机不透明串，后端仅存 SHA-256 哈希；明文不得进入日志、Trace、指标或数据库。前端引入后由 Next BFF 放入 `Secure`/`HttpOnly`/`SameSite` Cookie，禁止 localStorage/sessionStorage/IndexedDB；
- 请求幂等：M01 以 PostgreSQL `idempotency_records` 为事实源；可选 M05 Redis hardening 只允许作为可丢失的加速层，不阻塞 MVP；
- 删除策略：软禁用（status=DISABLED）优先，物理删除需审批；
- 审计字段：created_at/updated_at 由迁移定义，created_by 适用时记录；
- 迁移策略：forward-only；开发库失败清库重建，不提供 down migration。
