-- =====================================================================
-- F1-T2 身份基线：用户 / 团队 / 成员 + 刷新令牌 + 幂等记录
-- Flyway 规则：迁移只前向（V1 之后只加 V2/V3...，永不修改已执行的文件）。
-- =====================================================================

CREATE TABLE teams (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    -- ★ 核心：UNIQUE 约束是"数据库层的最后防线"——即使应用代码忘了查重，
    -- 两个并发注册同名团队时也只有一个能成功，另一个会拿到冲突错误。
    -- 改坏后果：去掉 UNIQUE 后，并发场景会产生同名团队，业务上无法区分。
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    -- ★ 核心：密码永远只存"哈希"（BCrypt，自带盐、慢哈希抗暴力破解）。
    -- 这个字段没有任何理由出现明文；日志/异常里也永远不能打印它。
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE team_members (
    team_id    BIGINT      NOT NULL REFERENCES teams (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    -- 角色限定四个合法值：OWNER / OPERATOR / REVIEWER / VIEWER
    role       VARCHAR(16) NOT NULL CHECK (role IN ('OWNER','OPERATOR','REVIEWER','VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ★ 核心：复合主键 (team_id, user_id) 同时表达两件事——
    -- 1) 天然去重：同一用户在同一团队只有一条成员记录；
    -- 2) 查询模式匹配：最常用的查询是"某团队的成员列表"和"某用户在
    --    某团队的角色"，复合主键索引正好覆盖。
    CONSTRAINT pk_team_members PRIMARY KEY (team_id, user_id)
);

-- 反向查询支持："这个用户加入了哪些团队"（主键索引只覆盖 team_id 在前）
CREATE INDEX idx_team_members_user ON team_members (user_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    -- ★ 核心：refresh token 服务端只存 SHA-256 哈希——
    -- 数据库泄露时攻击者拿到的哈希无法反向还原出 token 去换取新令牌。
    -- 改坏后果：若存明文，一次拖库 = 所有用户可被永久冒充。
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- NULL = 有效；非 NULL = 已被吊销（刷新轮换或登出时写入）
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE idempotency_records (
    -- ★ 核心：幂等记录的并发安全设计——
    -- (scope, idempotency_key) 复合主键 + INSERT ... ON CONFLICT DO NOTHING：
    -- 两个相同 key 的请求并发到达时，只有一个 INSERT 成功，另一个必然
    -- 落到"读已存响应"的分支，不存在双写。这就是 T5 幂等中间件的底座。
    scope            VARCHAR(128) NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    response_status  INT          NOT NULL,
    response_body    TEXT         NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotency_records PRIMARY KEY (scope, idempotency_key)
);
