-- =============================================================================
-- V3: 表与字段中文注释
-- 为 V1/V2 已有表补充标准 COMMENT，供可视化工具（DBeaver/DataGrip 等）展示。
-- 仅添加注释，不修改表结构和数据。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- frameflow_schema_baseline
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  frameflow_schema_baseline IS '数据库基线标记表（单例行，标记 Flyway 初始化完成）';
COMMENT ON COLUMN frameflow_schema_baseline.id             IS '固定为 1，保证全表只有一行基线记录';
COMMENT ON COLUMN frameflow_schema_baseline.initialized_at IS '基线初始化时间';

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  users IS '用户表 — 注册用户的基本信息与认证凭据';
COMMENT ON COLUMN users.id            IS '用户 ID（主键，自增）';
COMMENT ON COLUMN users.email         IS '邮箱（唯一，已小写规范化，用于登录）';
COMMENT ON COLUMN users.password_hash IS '密码哈希（BCrypt cost=10+，禁止明文存储/日志/响应/Token）';
COMMENT ON COLUMN users.display_name  IS '显示名称（用户昵称）';
COMMENT ON COLUMN users.status         IS '用户状态：ACTIVE=活跃 / DISABLED=已禁用 / PENDING=待激活';
COMMENT ON COLUMN users.last_login_at  IS '最近登录时间（NULL 表示从未登录）';
COMMENT ON COLUMN users.created_at     IS '创建时间';
COMMENT ON COLUMN users.updated_at     IS '更新时间';

-- -----------------------------------------------------------------------------
-- teams
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  teams IS '团队表 — 视频创作团队，资源隔离的顶层边界';
COMMENT ON COLUMN teams.id         IS '团队 ID（主键，自增）';
COMMENT ON COLUMN teams.name       IS '团队名称';
COMMENT ON COLUMN teams.created_by IS '创建者用户 ID（外键 → users.id）';
COMMENT ON COLUMN teams.created_at IS '创建时间';
COMMENT ON COLUMN teams.updated_at IS '更新时间';

-- -----------------------------------------------------------------------------
-- team_members
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  team_members IS '团队成员表 — 用户与团队的归属关系及团队角色（不含 CLIENT 项目角色）';
COMMENT ON COLUMN team_members.id         IS '成员关系 ID（主键，自增）';
COMMENT ON COLUMN team_members.team_id    IS '团队 ID（外键 → teams.id）';
COMMENT ON COLUMN team_members.user_id    IS '用户 ID（外键 → users.id）';
COMMENT ON COLUMN team_members.role       IS '团队角色：OWNER=团队管理员 / PRODUCER=制片 / EDITOR=剪辑 / VIEWER=只读';
COMMENT ON COLUMN team_members.status     IS '成员状态：ACTIVE=活跃 / REMOVED=已移除（软删除）';
COMMENT ON COLUMN team_members.created_at IS '创建时间';
COMMENT ON COLUMN team_members.updated_at IS '更新时间';

-- -----------------------------------------------------------------------------
-- refresh_token_sessions
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  refresh_token_sessions IS '刷新令牌会话表 — 每行代表一个 Refresh Token 代次，按 family_id 串成可整体撤销的会话';
COMMENT ON COLUMN refresh_token_sessions.id             IS '会话记录 ID（主键，自增）';
COMMENT ON COLUMN refresh_token_sessions.user_id        IS '用户 ID（外键 → users.id）';
COMMENT ON COLUMN refresh_token_sessions.family_id      IS '会话族 ID（UUID，同一设备登录及其轮换保持不变，可整体撤销）';
COMMENT ON COLUMN refresh_token_sessions.token_hash     IS 'Refresh Token 的 SHA-256 哈希（64 位十六进制，禁止存明文）';
COMMENT ON COLUMN refresh_token_sessions.status         IS '状态：ACTIVE=可用 / ROTATED=已轮换 / REVOKED=已撤销';
COMMENT ON COLUMN refresh_token_sessions.expires_at     IS '过期时间（过期后即使 status=ACTIVE 也不可用）';
COMMENT ON COLUMN refresh_token_sessions.last_used_at   IS '最近一次成功刷新时间';
COMMENT ON COLUMN refresh_token_sessions.rotated_at     IS '被轮换时间（新 Token 生成后旧 Token 标记为 ROTATED）';
COMMENT ON COLUMN refresh_token_sessions.revoked_at     IS '被撤销时间（登出或重放检测触发撤销）';
COMMENT ON COLUMN refresh_token_sessions.replaced_by_id IS '轮换后的新 Token 记录 ID（外键 → refresh_token_sessions.id，同一 user_id+family_id）';
COMMENT ON COLUMN refresh_token_sessions.created_at     IS '创建时间';
COMMENT ON COLUMN refresh_token_sessions.updated_at     IS '更新时间';

-- -----------------------------------------------------------------------------
-- idempotency_records
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  idempotency_records IS '请求幂等记录表 — PostgreSQL 权威事实源，保证高风险写操作同键只生效一次（M01 由 Identity 模块拥有）';
COMMENT ON COLUMN idempotency_records.id              IS '幂等记录 ID（主键，自增）';
COMMENT ON COLUMN idempotency_records.scope           IS '作用域（认证用户 + HTTP 方法 + 操作类型 + 目标资源组成的稳定标识）';
COMMENT ON COLUMN idempotency_records.idempotency_key IS '客户端幂等键（UUID，同一作用域内唯一）';
COMMENT ON COLUMN idempotency_records.request_hash    IS '请求指纹（规范化方法+路径+JSON body 的 SHA-256 哈希，同键不同哈希返回冲突）';
COMMENT ON COLUMN idempotency_records.status          IS '处理状态：PROCESSING=处理中 / COMPLETED=已完成';
COMMENT ON COLUMN idempotency_records.response_status IS '完成时的 HTTP 状态码（COMPLETED 时必填）';
COMMENT ON COLUMN idempotency_records.response_body   IS '完成时的业务响应 JSON（COMPLETED 时必填，禁止存 Token/Secret）';
COMMENT ON COLUMN idempotency_records.expires_at      IS '过期时间（创建后 24 小时，过期后允许新业务意图）';
COMMENT ON COLUMN idempotency_records.created_at      IS '创建时间';
COMMENT ON COLUMN idempotency_records.updated_at      IS '更新时间';
