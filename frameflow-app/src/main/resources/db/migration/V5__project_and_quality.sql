-- =============================================================================
-- V5: FrameFlow Select project & quality domain (S2, FF-DOM-002)
-- Owned by: project + quality modules.
-- Forward-only. Published quality profile versions are immutable.
-- =============================================================================

CREATE TABLE projects (
    id           BIGSERIAL PRIMARY KEY,
    team_id      BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    archived_at  TIMESTAMPTZ  NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_projects_team_name UNIQUE (team_id, name),
    CONSTRAINT fk_projects_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX idx_projects_team_created ON projects (team_id, created_at DESC);

CREATE TABLE quality_profiles (
    id             BIGSERIAL PRIMARY KEY,
    team_id        BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    name           VARCHAR(200) NOT NULL,
    template_type  VARCHAR(50)  NOT NULL DEFAULT 'ECOMMERCE_SHORT_AD_V1',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_profiles_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_profiles_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_profiles_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_profiles_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_profiles_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);
CREATE INDEX idx_profiles_team ON quality_profiles (team_id);

CREATE TABLE quality_profile_versions (
    id             BIGSERIAL PRIMARY KEY,
    profile_id     BIGINT       NOT NULL,
    version_no     INTEGER      NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    payload        JSONB        NOT NULL,
    payload_digest CHAR(64)     NOT NULL,
    published_at   TIMESTAMPTZ  NULL,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_profile_versions UNIQUE (profile_id, version_no),
    CONSTRAINT fk_profile_versions_profile FOREIGN KEY (profile_id) REFERENCES quality_profiles (id),
    CONSTRAINT fk_profile_versions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_profile_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);
CREATE INDEX idx_profile_versions_status ON quality_profile_versions (profile_id, status);

CREATE TABLE brief_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    team_id     BIGINT      NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT        NOT NULL,
    assertions  JSONB       NOT NULL,
    payload_digest CHAR(64) NOT NULL,
    created_by  BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_briefs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_briefs_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_briefs_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX idx_briefs_project ON brief_snapshots (project_id, created_at DESC);
