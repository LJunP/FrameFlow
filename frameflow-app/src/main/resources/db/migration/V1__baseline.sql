CREATE TABLE frameflow_schema_baseline (
    id SMALLINT PRIMARY KEY,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_frameflow_schema_baseline_singleton CHECK (id = 1)
);

INSERT INTO frameflow_schema_baseline (id) VALUES (1);
