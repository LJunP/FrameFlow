-- =====================================================================
-- F6 语义质检：语义判定 verdict + REVIEW_REQUIRED 候选状态
-- =====================================================================

-- 候选状态机补上 docs/01 §7 的 REVIEW_REQUIRED：
-- 语义 VIOLATE/UNKNOWN/ERROR 一律进人工复核——机器不定生死
ALTER TABLE candidates DROP CONSTRAINT candidates_status_check;
ALTER TABLE candidates ADD CONSTRAINT candidates_status_check CHECK (status IN (
    'PENDING_UPLOAD', 'UPLOADED', 'INVALID',
    'ANALYZING', 'ANALYZED', 'AUTO_REJECT', 'ANALYSIS_ERROR',
    'REVIEW_REQUIRED'));

-- 语义判定四值（docs/01 §8.2）：PASS / VIOLATE / UNKNOWN / ERROR
-- 确定性 Finding 的 verdict 为 NULL（它们用 passed 表达，语义用 verdict 表达）
ALTER TABLE findings ADD COLUMN verdict VARCHAR(16)
    CHECK (verdict IN ('PASS', 'VIOLATE', 'UNKNOWN', 'ERROR') OR verdict IS NULL);

-- ★ 核心：部分索引——只有语义行才有 verdict，绝大多数行是确定性 Finding。
-- 带 WHERE 的索引不为 NULL 行浪费一页一页的空间，语义查询却照样走索引。
CREATE INDEX idx_findings_semantic ON findings (candidate_id, verdict)
    WHERE verdict IS NOT NULL;
