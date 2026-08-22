-- =====================================================================
-- F2 产品配置基线：projects / briefs（不可变快照）/ quality_profiles(_versions)
-- 迁移只前向：V1 已在 F1 执行，本文件只新增，不回改 V1。
-- =====================================================================

CREATE TABLE projects (
    id               BIGSERIAL PRIMARY KEY,
    team_id          BIGINT       NOT NULL REFERENCES teams (id),
    name             VARCHAR(96)  NOT NULL,
    description      VARCHAR(512),
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- 当前生效的 Brief 快照（业务指针可变，快照本身不可变，见 briefs 表）
    current_brief_id BIGINT,
    created_by       BIGINT       NOT NULL REFERENCES users (id),
    -- ★ 核心：乐观锁——并发修改同一项目时，后提交者携带的旧 lockVersion
    -- 匹配不上 UPDATE 的 WHERE 条件，更新影响行数为 0，据此返回 409。
    -- 相比悲观锁（SELECT FOR UPDATE），乐观锁不阻塞读、不占用数据库锁，
    -- 适合"冲突概率低"的配置类数据。
    lock_version     INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- ★ 核心：(team_id, name) 唯一——项目名冲突的数据库最后防线，
    -- 与 F1 邮箱唯一约束同一思想：应用查重挡 99%，UNIQUE 挡并发缝隙。
    CONSTRAINT uq_projects_team_name UNIQUE (team_id, name)
);

CREATE INDEX idx_projects_team ON projects (team_id);

CREATE TABLE briefs (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    created_by BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    -- ★ 核心：不可变快照的建模——这张表【没有】任何 UPDATE 语义的列：
    -- 无 updated_at、无可编辑字段。"修正 = 追加新行 + 指针前移"。
    -- 为什么：批次(F3)要引用"创建当时的 Brief"；可变数据做不到历史还原，
    -- 不可变快照让任何时间点的配置都可精确复现。
);

CREATE INDEX idx_briefs_project ON briefs (project_id, id);

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_current_brief
    FOREIGN KEY (current_brief_id) REFERENCES briefs (id);

-- 质检标准集合：团队级资产（可跨项目复用），具体标准在版本表里
CREATE TABLE quality_profiles (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT       NOT NULL REFERENCES teams (id),
    name        VARCHAR(96)  NOT NULL,
    description VARCHAR(512),
    created_by  BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_quality_profiles_team_name UNIQUE (team_id, name)
);

CREATE INDEX idx_quality_profiles_team ON quality_profiles (team_id);

CREATE TABLE quality_profile_versions (
    id           BIGSERIAL PRIMARY KEY,
    profile_id   BIGINT      NOT NULL REFERENCES quality_profiles (id) ON DELETE CASCADE,
    version_no   INT         NOT NULL,
    -- ★ 核心：spec 用 JSONB 而非 TEXT——数据库知道它是结构化文档，
    -- 将来可直接查询（如统计哪些版本启用了某维度），且入库时校验合法性。
    spec         JSONB       NOT NULL,
    published_by BIGINT      NOT NULL REFERENCES users (id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ★ 核心：版本化配置的引用隔离——(profile_id, version_no) 唯一 +
    -- version_no 只增不覆写。批次(F3)引用的是"某一个版本行"而不是 profile，
    -- 因此发布新版永远不会改变历史批次的质检标准。这就是"历史批次
    -- 永远引用旧版本"在物理层的保证。
    CONSTRAINT uq_profile_version UNIQUE (profile_id, version_no),
    CONSTRAINT ck_profile_version_positive CHECK (version_no >= 1)
);

CREATE INDEX idx_profile_versions_profile ON quality_profile_versions (profile_id, version_no);
