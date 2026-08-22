-- =====================================================================
-- F4 确定性质检流水线：analysis_runs / findings + 候选状态机扩展
-- =====================================================================

-- 候选状态机扩展（docs/01 §7）：
-- UPLOADED → ANALYZING → ANALYZED（有缺陷但可人工看）
--                     → AUTO_REJECT（仅确定性 BLOCKER 规则可触发）
--                     → ANALYSIS_ERROR（worker/检测器故障——系统问题，严禁伪装成视频问题）
ALTER TABLE candidates DROP CONSTRAINT candidates_status_check;
ALTER TABLE candidates ADD CONSTRAINT candidates_status_check CHECK (status IN (
    'PENDING_UPLOAD', 'UPLOADED', 'INVALID',
    'ANALYZING', 'ANALYZED', 'AUTO_REJECT', 'ANALYSIS_ERROR'));

CREATE TABLE analysis_runs (
    id             BIGSERIAL PRIMARY KEY,
    candidate_id   BIGINT      NOT NULL REFERENCES candidates (id),
    batch_id       BIGINT      NOT NULL REFERENCES generation_batches (id),
    worker_version VARCHAR(64),
    -- RUNNING → SUCCEEDED / FAILED（CANCELLED 留给 F7 人工干预）
    status         VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
                   CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    error_summary  TEXT,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ
);

CREATE INDEX idx_runs_candidate ON analysis_runs (candidate_id);
CREATE INDEX idx_runs_batch ON analysis_runs (batch_id);

CREATE TABLE findings (
    id               BIGSERIAL PRIMARY KEY,
    run_id           BIGINT       NOT NULL REFERENCES analysis_runs (id),
    candidate_id     BIGINT       NOT NULL REFERENCES candidates (id),
    detector         VARCHAR(64)  NOT NULL,
    detector_version VARCHAR(32)  NOT NULL,
    -- 检测维度：duration / resolution / fps / aspect_ratio / black_frame /
    -- freeze / silence / no_audio（确定性维度全集，语义见 docs/01 §8.1）
    dimension        VARCHAR(32)  NOT NULL,
    -- passed=true 的行也要存——它是"检查确实做过"的证据（审计与复现）
    passed           BOOLEAN      NOT NULL,
    -- ★ 核心：只有 BLOCKER 级别的未通过才允许触发 AUTO_REJECT；
    -- WARNING 只进人工复核。机器的"生杀大权"被 severity 锁死。
    severity         VARCHAR(16)  NOT NULL DEFAULT 'INFO'
                     CHECK (severity IN ('BLOCKER', 'WARNING', 'INFO')),
    timecode_ms      BIGINT,
    -- 证据束：原始输出片段/关键帧引用/命中区间（JSONB，可检索）
    evidence         JSONB,
    message          VARCHAR(512)
);

CREATE INDEX idx_findings_candidate ON findings (candidate_id, dimension);
