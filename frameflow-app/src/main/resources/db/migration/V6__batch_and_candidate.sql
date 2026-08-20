-- =============================================================================
-- V6: batch & candidate domain (S2/S4)
-- Forward-only. Candidate source files are immutable; replacement creates a new
-- candidate version. Candidate technical manifest records deterministic media facts.
-- =============================================================================

CREATE TABLE batches (
    id             BIGSERIAL PRIMARY KEY,
    team_id        BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    name           VARCHAR(200) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    prompt_text    TEXT         NULL,
    profile_version_id BIGINT   NULL,
    brief_id       BIGINT       NULL,
    capacity_limit INTEGER      NOT NULL DEFAULT 300,
    failure_count  INTEGER      NOT NULL DEFAULT 0,
    created_by     BIGINT       NOT NULL,
    started_at     TIMESTAMPTZ  NULL,
    completed_at   TIMESTAMPTZ  NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_batches_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_batches_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_batches_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_batches_profile_version FOREIGN KEY (profile_version_id) REFERENCES quality_profile_versions (id),
    CONSTRAINT fk_batches_brief FOREIGN KEY (brief_id) REFERENCES brief_snapshots (id),
    CONSTRAINT fk_batches_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_batches_status CHECK (status IN ('DRAFT','UPLOADING','READY','QUEUED','PROCESSING','REVIEW_READY','PARTIAL_FAILURE','FAILED','CANCELLED','SELECTING','COMPLETED'))
);
CREATE INDEX idx_batches_team ON batches (team_id, created_at DESC);
CREATE INDEX idx_batches_project ON batches (project_id, created_at DESC);

CREATE TABLE candidates (
    id             BIGSERIAL PRIMARY KEY,
    team_id        BIGINT       NOT NULL,
    batch_id       BIGINT       NOT NULL,
    candidate_key  VARCHAR(100) NOT NULL,    -- client-provided dedup key / file stem
    status         VARCHAR(20)  NOT NULL DEFAULT 'UPLOADING',
    parent_candidate_id BIGINT   NULL,
    media_type     VARCHAR(50)  NOT NULL DEFAULT 'video/mp4',
    size_bytes     BIGINT       NOT NULL DEFAULT 0,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidates_batch_key UNIQUE (batch_id, candidate_key),
    CONSTRAINT fk_candidates_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_candidates_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_candidates_parent FOREIGN KEY (parent_candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_candidates_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_candidates_status CHECK (status IN ('UPLOADING','STORED','READY_FOR_ANALYSIS','ANALYZING','ANALYZED','ANALYSIS_ERROR','INVALID','UPLOAD_FAILED'))
);
CREATE INDEX idx_candidates_batch ON candidates (batch_id);

CREATE TABLE candidate_versions (
    id             BIGSERIAL PRIMARY KEY,
    candidate_id   BIGINT       NOT NULL,
    version_no     INTEGER      NOT NULL,
    object_ref     TEXT         NOT NULL,
    content_digest CHAR(64)     NOT NULL,
    size_bytes     BIGINT       NOT NULL DEFAULT 0,
    media_type     VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidate_versions UNIQUE (candidate_id, version_no),
    CONSTRAINT fk_candversions_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
);

CREATE TABLE candidate_technical_manifests (
    id            BIGSERIAL PRIMARY KEY,
    candidate_version_id BIGINT NOT NULL,
    facts         JSONB       NOT NULL,     -- ffprobe media facts
    probe_version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_manifest_candidate_version UNIQUE (candidate_version_id),
    CONSTRAINT fk_manifests_candidate_version FOREIGN KEY (candidate_version_id) REFERENCES candidate_versions (id)
);

CREATE TABLE upload_sessions (
    id             BIGSERIAL PRIMARY KEY,
    candidate_id   BIGINT       NOT NULL,
    team_id        BIGINT       NOT NULL,
    storage_key    TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',   -- PENDING | UPLOADING | COMPLETED | FAILED
    expected_size  BIGINT       NULL,
    expected_digest CHAR(64)    NULL,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upsessions_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_upsessions_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT ck_upsessions_status CHECK (status IN ('PENDING','UPLOADING','COMPLETED','FAILED'))
);
CREATE INDEX idx_upsessions_candidate ON upload_sessions (candidate_id);
