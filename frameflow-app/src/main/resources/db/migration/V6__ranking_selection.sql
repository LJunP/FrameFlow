-- =====================================================================
-- F7 聚类排名与优选：重复检测特征列 + 排名快照 + 优选集
-- =====================================================================

-- worker 在确定性质检阶段回填的两类"指纹"：
-- content_hash：文件字节级 SHA-256（精确重复判定，永不冲突）
-- phash：首帧感知哈希 dHash 64bit（十六进制存 CHAR(16)）——近重复判定
ALTER TABLE candidates ADD COLUMN content_hash CHAR(64);
ALTER TABLE candidates ADD COLUMN phash CHAR(16);
CREATE INDEX idx_candidates_batch_phash ON candidates (batch_id)
    WHERE phash IS NOT NULL AND status IN ('ANALYZED', 'REVIEW_REQUIRED');

-- 排名快照：一次排名的完整固化（分数 + 聚类 + 构成），只增不改
CREATE TABLE ranking_snapshots (
    id                 BIGSERIAL PRIMARY KEY,
    batch_id           BIGINT      NOT NULL REFERENCES generation_batches (id),
    profile_version_id BIGINT      NOT NULL REFERENCES quality_profile_versions (id),
    -- 算法版本独立于 profile 版本：换算法（如聚类策略）即使 profile 没变
    -- 也要能区分两次快照的可比性
    algorithm_version  VARCHAR(32) NOT NULL,
    -- 聚类阈值来源：快照记录当时用的值（阈值配置在 profile spec 里，
    -- 随版本冻结；这里冗余记录便于直接复现与审计）
    hamming_threshold  INT         NOT NULL,
    created_by         BIGINT      NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ranking_entries (
    id             BIGSERIAL PRIMARY KEY,
    snapshot_id    BIGINT      NOT NULL REFERENCES ranking_snapshots (id),
    candidate_id   BIGINT      NOT NULL REFERENCES candidates (id),
    rank_no        INT         NOT NULL,
    cluster_id     INT         NOT NULL,        -- 快照内局部编号（稳定：按代表 id 排序后编号）
    is_representative BOOLEAN  NOT NULL,
    score          INT         NOT NULL,
    -- 可解释性：每一分扣减的明细（dimension × weight），审阅者能回答"为什么是这个分数"
    breakdown      JSONB       NOT NULL,
    excluded_reason VARCHAR(64),
    UNIQUE (snapshot_id, candidate_id)
);
CREATE INDEX idx_ranking_entries_snapshot ON ranking_entries (snapshot_id, rank_no);

-- 优选集：机器推荐（Top-K）+ 人工叠加调整 → 锁定导出
CREATE TABLE selection_sets (
    id          BIGSERIAL PRIMARY KEY,
    batch_id    BIGINT      NOT NULL REFERENCES generation_batches (id),
    snapshot_id BIGINT      NOT NULL REFERENCES ranking_snapshots (id),
    -- ★ 核心：DRAFT → LOCKED 单向；锁定后任何写操作都被条件更新挡住，
    -- 导出的结果永远对应一个不可变的优选集（交付可复现）
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'LOCKED')),
    top_k       INT         NOT NULL,
    created_by  BIGINT      NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at   TIMESTAMPTZ,
    locked_by   BIGINT REFERENCES users (id)
);

CREATE TABLE selection_items (
    id            BIGSERIAL PRIMARY KEY,
    selection_id  BIGINT      NOT NULL REFERENCES selection_sets (id),
    candidate_id  BIGINT      NOT NULL REFERENCES candidates (id),
    -- machine_pick：排名机器的选择（Top-K 内为 true）
    -- human_action：人工叠加（INCLUDE / EXCLUDE / NULL 未动）
    -- ★ 核心：人工调整不改写机器结果——两列并存，导出时各表各的，
    -- 事后审计能还原"机器选了什么、人改了什么"（docs/01 §9）
    machine_pick  BOOLEAN     NOT NULL,
    human_action  VARCHAR(16),
    note          VARCHAR(512),
    UNIQUE (selection_id, candidate_id)
);
