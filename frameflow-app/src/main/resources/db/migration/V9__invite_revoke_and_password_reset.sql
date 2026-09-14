-- =====================================================================
-- 邀请可撤销 + 自助找回密码（V9，只前向；不修改 V7/V8）。
--
-- 1) team_invitations.revoked_at：Owner 作废未接受的邀请。
--    与 accepted_at 互斥使用——已接受的邀请不能再撤销（成员关系已成立）。
-- 2) password_reset_tokens：找回密码令牌只存 SHA-256，与 refresh / 邀请
--    同一强度。明文只在生成当刻存在（本地日志或测试），库泄露不可还原。
-- =====================================================================

ALTER TABLE team_invitations
    ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
