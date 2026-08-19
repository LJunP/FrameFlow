-- =============================================================================
-- V2: Identity module tables (M01)
-- Owned by the identity module: users / teams / team_members /
-- refresh_token_sessions / idempotency_records.
-- Forward-only. project_members (team-level project membership) belongs to the
-- M02 project module and MUST NOT be created here.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'PENDING'))
);

-- -----------------------------------------------------------------------------
-- teams
-- -----------------------------------------------------------------------------
CREATE TABLE teams (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_by BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

-- -----------------------------------------------------------------------------
-- team_members (CLIENT is a project-level role of M02; it is not allowed here)
-- -----------------------------------------------------------------------------
CREATE TABLE team_members (
    id         BIGSERIAL PRIMARY KEY,
    team_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_team_members_role   CHECK (role IN ('OWNER', 'PRODUCER', 'EDITOR', 'VIEWER')),
    CONSTRAINT ck_team_members_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

CREATE INDEX idx_team_members_user_status ON team_members (user_id, status);
CREATE INDEX idx_team_members_team_role ON team_members (team_id, role);

-- -----------------------------------------------------------------------------
-- refresh_token_sessions (one row per refresh token generation/family)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_token_sessions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    family_id      UUID        NOT NULL,
    token_hash     CHAR(64)    NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at     TIMESTAMPTZ NOT NULL,
    last_used_at   TIMESTAMPTZ NULL,
    rotated_at     TIMESTAMPTZ NULL,
    revoked_at     TIMESTAMPTZ NULL,
    replaced_by_id BIGINT      NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_token_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_sessions_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token_sessions (id),
    CONSTRAINT ck_refresh_sessions_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED'))
);

CREATE INDEX idx_refresh_sessions_user_status ON refresh_token_sessions (user_id, status);
CREATE INDEX idx_refresh_sessions_family_status ON refresh_token_sessions (family_id, status);
CREATE INDEX idx_refresh_sessions_expires_at ON refresh_token_sessions (expires_at);

-- -----------------------------------------------------------------------------
-- idempotency_records (request deduplication fact source, M01: owned by identity)
-- -----------------------------------------------------------------------------
CREATE TABLE idempotency_records (
    id              BIGSERIAL PRIMARY KEY,
    scope           VARCHAR(255) NOT NULL,
    idempotency_key UUID         NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    response_status INTEGER      NULL,
    response_body   JSONB        NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_records_scope_key UNIQUE (scope, idempotency_key),
    CONSTRAINT ck_idempotency_records_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_idempotency_records_completed CHECK (
        status <> 'COMPLETED' OR (response_status IS NOT NULL AND response_body IS NOT NULL)
    )
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records (expires_at);
CREATE INDEX idx_idempotency_records_status_updated ON idempotency_records (status, updated_at);
