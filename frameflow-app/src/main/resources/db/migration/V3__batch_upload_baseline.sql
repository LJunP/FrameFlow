-- =====================================================================
-- F3 批次与上传：generation_batches / candidates
-- =====================================================================

CREATE TABLE generation_batches (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT      NOT NULL REFERENCES projects (id),
    -- ★ 核心：批次绑定的是 profile 的【具体版本行】而非 profile 本身，
    -- 同时绑定创建时刻的 Brief 快照。这两列在批次创建后永不更新——
    -- 这就是"历史批次永远按当时的标准与要求质检"在数据层的落点
    -- （配合 F2 的版本不可变约束形成完整闭环）。
    profile_version_id  BIGINT      NOT NULL REFERENCES quality_profile_versions (id),
    brief_id            BIGINT      NOT NULL REFERENCES briefs (id),
    -- F3 阶段：OPEN 可上传；CLOSED 停止接收（分析状态从 F4 起扩展）
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'CLOSED')),
    -- ★ 核心：有界容量——批次必须能"装得下"但不许无限膨胀，
    -- 这是 F4 批次调度的背压前提。数据库 CHECK 是最后防线。
    capacity            INT         NOT NULL CHECK (capacity BETWEEN 1 AND 300),
    created_by          BIGINT      NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_batches_project ON generation_batches (project_id);

CREATE TABLE candidates (
    id           BIGSERIAL PRIMARY KEY,
    batch_id     BIGINT       NOT NULL REFERENCES generation_batches (id),
    -- F3 候选状态机（F4 起扩展 ANALYZING/ANALYZED/...，见 docs/01 §7）：
    -- PENDING_UPLOAD：已登记，等待客户端直传
    -- UPLOADED：直传完成且通过入口校验（存在性/大小/媒体签名）
    -- INVALID：入口校验失败，probe_error 记录证据（坏文件≠分析失败）
    status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING_UPLOAD'
                 CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED', 'INVALID')),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    -- ★ 核心：字节在 MinIO、元数据在 PG 的分离——object_key 是两者
    -- 的唯一联系。分离带来"必须对账"的一致性问题（见 ReconcileService），
    -- 换来的是：PG 不被大文件拖垮、存储可换实现、上传不经过应用服务器。
    object_key   VARCHAR(512) NOT NULL,
    upload_mode  VARCHAR(16),
    s3_upload_id VARCHAR(256),
    etag         VARCHAR(128),
    -- 媒体探针结果（F4 由 Python worker 用 ffprobe 填写）
    duration_ms  BIGINT,
    width        INT,
    height       INT,
    fps          DOUBLE PRECISION,
    -- INVALID 的证据留痕（坏文件必须能回答"为什么被判无效"）
    probe_error  TEXT,
    created_by   BIGINT       NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    uploaded_at  TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_candidates_batch ON candidates (batch_id, status);
