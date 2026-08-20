-- =============================================================================
-- V7: analysis, findings, worker dispatch, evidence (S2/S3/S5)
-- Python worker never owns these tables; PostgreSQL is the final business state.
-- Forward-only.
-- =============================================================================

CREATE TABLE analysis_runs (
    id            BIGSERIAL PRIMARY KEY,
    team_id       BIGINT       NOT NULL,
    batch_id      BIGINT       NOT NULL,
    candidate_version_id BIGINT NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'CREATED',   -- CREATED|QUEUED|RUNNING|COMPLETED|COMPLETED_WITH_FINDINGS|FAILED|CANCELLED|TIMED_OUT
    command_id    VARCHAR(64)  NULL,
    result_digest CHAR(64)     NULL,
    decision      JSONB        NULL,      -- {value, automatic, reasons}
    quality_vector JSONB       NULL,
    started_at    TIMESTAMPTZ  NULL,
    completed_at  TIMESTAMPTZ  NULL,
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_runs_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_runs_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_runs_candidate_version FOREIGN KEY (candidate_version_id) REFERENCES candidate_versions (id),
    CONSTRAINT fk_runs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_runs_status CHECK (status IN ('CREATED','QUEUED','RUNNING','COMPLETED','COMPLETED_WITH_FINDINGS','FAILED','CANCELLED','TIMED_OUT'))
);
CREATE INDEX idx_runs_batch ON analysis_runs (batch_id);
CREATE INDEX idx_runs_candidate_version ON analysis_runs (candidate_version_id);

CREATE TABLE analysis_stage_jobs (
    id            BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT     NOT NULL,
    stage         VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payload       JSONB        NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stagejobs_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_runs (id),
    CONSTRAINT ck_stagejobs_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED'))
);

CREATE TABLE findings (
    id             BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT      NOT NULL,
    candidate_id   BIGINT       NOT NULL,
    rule_id        VARCHAR(100) NOT NULL,
    rule_version   INTEGER      NOT NULL DEFAULT 1,
    detector_id    VARCHAR(100) NULL,
    detector_version VARCHAR(50) NULL,
    dimension      VARCHAR(50)  NOT NULL,
    finding_type   VARCHAR(50)  NULL,
    verdict        VARCHAR(20)  NOT NULL,    -- SATISFIED|VIOLATED|UNKNOWN
    severity       VARCHAR(20)  NOT NULL,    -- BLOCKER|MAJOR|MINOR|INFO
    confidence     NUMERIC(6,5) NOT NULL,
    automation_action VARCHAR(20) NOT NULL DEFAULT 'REVIEW',   -- REJECT|REVIEW|INFO
    start_ms       INTEGER      NULL,
    end_ms         INTEGER      NULL,
    summary        TEXT         NOT NULL,
    evidence       JSONB        NOT NULL DEFAULT '{}',
    origin         JSONB        NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_findings_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_runs (id),
    CONSTRAINT fk_findings_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT ck_findings_verdict CHECK (verdict IN ('SATISFIED','VIOLATED','UNKNOWN')),
    CONSTRAINT ck_findings_severity CHECK (severity IN ('BLOCKER','MAJOR','MINOR','INFO'))
);
CREATE INDEX idx_findings_run ON findings (analysis_run_id);
CREATE INDEX idx_findings_candidate ON findings (candidate_id);

CREATE TABLE finding_evidence (
    id            BIGSERIAL PRIMARY KEY,
    finding_id    BIGINT       NOT NULL,
    evidence_type VARCHAR(30)  NOT NULL,   -- METADATA|FRAME|FRAME_PAIR|VIDEO_CLIP|AUDIO_CLIP|TRANSCRIPT|OCR_SPAN
    object_ref    TEXT         NULL,
    content_digest CHAR(64)    NULL,
    start_ms      INTEGER      NULL,
    end_ms        INTEGER      NULL,
    payload       JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_finding FOREIGN KEY (finding_id) REFERENCES findings (id)
);
CREATE INDEX idx_evidence_finding ON finding_evidence (finding_id);

-- outbox: transactional async command dispatch (S3) ---------------------------
CREATE TABLE outbox_records (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id  BIGINT       NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    payload       JSONB        NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',   -- PENDING|PUBLISHED|FAILED
    published_at  TIMESTAMPTZ  NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);
CREATE INDEX idx_outbox_status ON outbox_records (status, created_at);
