-- 邮箱验证、分析任务 Outbox。只前向。
-- 已有用户 email_verified_at 为空：不阻断登录，由前端提示补验证。

ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);

-- ★ 核心：同一事务写入 run 行与 outbox，提交后再发 MQ。
-- 避免「消息已发出、事务回滚」造成 worker 回写 404。
CREATE TABLE analysis_outbox (
    id           BIGSERIAL PRIMARY KEY,
    run_id       BIGINT      NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_analysis_outbox_run UNIQUE (run_id)
);

CREATE INDEX idx_analysis_outbox_unpublished
    ON analysis_outbox (id)
    WHERE published_at IS NULL;
