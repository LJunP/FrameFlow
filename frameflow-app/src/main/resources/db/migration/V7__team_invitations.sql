-- =====================================================================
-- 团队成员邀请：team_invitations（V1-V6 已存在，本迁移顺延为 V7；
-- Flyway 规则：迁移只前向，永不修改已执行的文件）。
-- =====================================================================

CREATE TABLE team_invitations (
    id         BIGSERIAL PRIMARY KEY,
    team_id    BIGINT       NOT NULL REFERENCES teams (id),
    email      VARCHAR(255) NOT NULL,
    -- ★ 核心：邀请角色只允许三个非 OWNER 值——OWNER 由注册路径唯一创建，
    -- 邀请通道永远不能再造一个 Owner，否则"团队只有一个最高权限者"
    -- 这条不变量就被旁路击穿。CHECK 是数据库层的最后防线。
    role       VARCHAR(16)  NOT NULL CHECK (role IN ('OPERATOR','REVIEWER','VIEWER')),
    -- 邀请令牌：64 位十六进制（32 字节密码学随机数），等价 256bit 熵，
    -- 不可枚举；UNIQUE 兜底防重复。令牌本身就是凭证，任何人持有即可接受邀请。
    token      CHAR(64)     NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    -- NULL = 待接受；非 NULL = 已使用（一次性，防止同一链接重复拉人入团）
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    invited_by BIGINT       NOT NULL REFERENCES users (id),
    CONSTRAINT uq_team_invitations_token UNIQUE (token)
);

-- Owner 在团队页查看"待接受邀请列表"的查询路径
CREATE INDEX idx_team_invitations_team ON team_invitations (team_id);
