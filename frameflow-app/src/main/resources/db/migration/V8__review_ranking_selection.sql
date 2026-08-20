-- =============================================================================
-- V8: review, clustering, ranking & selection (S6)
-- Machine results and human overrides are separate facts (never merged).
-- Forward-only.
-- =============================================================================

CREATE TABLE human_finding_reviews (
    id            BIGSERIAL PRIMARY KEY,
    finding_id    BIGINT       NOT NULL,
    candidate_id  BIGINT       NOT NULL,
    disposition   VARCHAR(20)  NOT NULL,
    reason_code   VARCHAR(40)  NOT NULL,
    note          TEXT         NULL,
    reviewed_by   BIGINT       NOT NULL,
    reviewed_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_finding FOREIGN KEY (finding_id) REFERENCES findings (id),
    CONSTRAINT fk_reviews_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_reviews_disposition CHECK (disposition IN ('CONFIRMED','REJECTED','MODIFIED')),
    CONSTRAINT ck_reviews_reason CHECK (reason_code IN ('FALSE_POSITIVE','FALSE_NEGATIVE','BUSINESS_EXCEPTION','CREATIVE_PREFERENCE','BAD_EVIDENCE','WRONG_SEVERITY','OTHER'))
);
CREATE INDEX idx_reviews_finding ON human_finding_reviews (finding_id);

CREATE TABLE candidate_human_decisions (
    id            BIGSERIAL PRIMARY KEY,
    candidate_id  BIGINT       NOT NULL,
    batch_id      BIGINT       NOT NULL,
    decision      VARCHAR(20)  NOT NULL,
    note          TEXT         NULL,
    decided_by    BIGINT       NOT NULL,
    decided_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_canddecisions_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_canddecisions_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_canddecisions_user FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT ck_canddecisions CHECK (decision IN ('KEEP','REJECT','REVIEW'))
);
CREATE INDEX idx_canddecisions_candidate ON candidate_human_decisions (candidate_id);

CREATE TABLE similarity_clusters (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      BIGINT       NOT NULL,
    cluster_key   VARCHAR(50)  NOT NULL,
    threshold     NUMERIC(6,5) NOT NULL,
    algorithm_version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    representative_candidate_id BIGINT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_clusters_batch_key UNIQUE (batch_id, cluster_key),
    CONSTRAINT fk_clusters_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_clusters_rep FOREIGN KEY (representative_candidate_id) REFERENCES candidates (id)
);
CREATE INDEX idx_clusters_batch ON similarity_clusters (batch_id);

CREATE TABLE cluster_members (
    cluster_id    BIGINT NOT NULL,
    candidate_id  BIGINT NOT NULL,
    similarity    NUMERIC(6,5) NOT NULL,
    PRIMARY KEY (cluster_id, candidate_id),
    CONSTRAINT fk_clm_cluster FOREIGN KEY (cluster_id) REFERENCES similarity_clusters (id),
    CONSTRAINT fk_clm_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
);

CREATE TABLE ranking_snapshots (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      BIGINT       NOT NULL,
    snapshot_version INTEGER    NOT NULL,
    algorithm_version VARCHAR(30) NOT NULL,
    weights       JSONB        NOT NULL,
    feature_schema_version VARCHAR(30) NOT NULL,
    created_by    BIGINT       NOT NULL,
    locked_at     TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rankingsnapshot_batch_version UNIQUE (batch_id, snapshot_version),
    CONSTRAINT fk_rankingsnapshot_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_rankingsnapshot_user FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE ranking_entries (
    ranking_snapshot_id BIGINT NOT NULL,
    candidate_id        BIGINT NOT NULL,
    rank                INTEGER NOT NULL,
    base_score          NUMERIC(7,5) NOT NULL,
    final_score         NUMERIC(7,5) NOT NULL,
    breakdown           JSONB  NOT NULL DEFAULT '{}',
    PRIMARY KEY (ranking_snapshot_id, candidate_id),
    CONSTRAINT fk_rankentry_snapshot FOREIGN KEY (ranking_snapshot_id) REFERENCES ranking_snapshots (id),
    CONSTRAINT fk_rankentry_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
);

CREATE TABLE selection_sets (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      BIGINT       NOT NULL,
    name          VARCHAR(200) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    top_k         INTEGER      NOT NULL,
    theme         VARCHAR(200) NULL,
    locked_by     BIGINT       NULL,
    locked_at     TIMESTAMPTZ  NULL,
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_selectionset_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_selectionset_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_selectionset_locker FOREIGN KEY (locked_by) REFERENCES users (id),
    CONSTRAINT ck_selectionset_status CHECK (status IN ('DRAFT','LOCKED','SUPERSEDED'))
);
CREATE INDEX idx_selectionset_batch ON selection_sets (batch_id);

CREATE TABLE selection_items (
    selection_set_id BIGINT NOT NULL,
    candidate_id     BIGINT NOT NULL,
    seq              INTEGER NOT NULL,
    cluster_id       BIGINT NULL,
    exported         BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (selection_set_id, candidate_id),
    CONSTRAINT fk_selitem_set FOREIGN KEY (selection_set_id) REFERENCES selection_sets (id),
    CONSTRAINT fk_selitem_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_selitem_cluster FOREIGN KEY (cluster_id) REFERENCES similarity_clusters (id)
);

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    team_id       BIGINT       NULL,
    actor_id      BIGINT       NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50)  NULL,
    resource_id   BIGINT       NULL,
    detail        JSONB        NOT NULL DEFAULT '{}',
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_audit_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
CREATE INDEX idx_audit_team_time ON audit_logs (team_id, occurred_at DESC);
