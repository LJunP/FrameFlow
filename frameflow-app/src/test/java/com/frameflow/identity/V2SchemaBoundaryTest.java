package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TEST-FF-M01-001-14 V2 Identity 表集合与 project_members 排除边界 */
class V2SchemaBoundaryTest extends IdentityIntegrationTestBase {

    @Test
    void test14_v2SchemaBoundary() {
        // project_members 属于 M02 project 模块，不得出现在 V2；NULL_PUBLISH 设计在批准前一律不得出现
        List<String> projectMemberTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'project_members'",
                String.class);
        assertThat(projectMemberTables).isEmpty();

        List<String> allTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
        // V2 identity 五表 + 基线/历史 + FrameFlow Select 产品模块表（V5-V8）
        assertThat(allTables).contains(
                "frameflow_schema_baseline", "users", "teams", "team_members",
                "refresh_token_sessions", "idempotency_records", "flyway_schema_history",
                "projects", "quality_profiles", "quality_profile_versions", "brief_snapshots",
                "batches", "candidates", "candidate_versions", "candidate_technical_manifests",
                "upload_sessions", "analysis_runs", "analysis_stage_jobs", "findings",
                "finding_evidence", "outbox_records", "human_finding_reviews",
                "candidate_human_decisions", "similarity_clusters", "cluster_members",
                "ranking_snapshots", "ranking_entries", "selection_sets", "selection_items",
                "audit_logs");
        assertThat(allTables).doesNotContain("project_members");

        // team_members.role CHECK 只允许 OWNER/OPERATOR/REVIEWER/VIEWER（V4），不包含 CLIENT
        String roleCheck = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_team_members_role'",
                String.class);
        assertThat(roleCheck).contains("OWNER", "OPERATOR", "REVIEWER", "VIEWER").doesNotContain("CLIENT");

        // 用户状态约束与幂等 status 约束存在
        String idemCheck = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_idempotency_records_status'",
                String.class);
        assertThat(idemCheck).contains("PROCESSING", "COMPLETED");

        // COMPLETED 必须有响应状态与响应体（不允许伪造成功）
        String completedCheck = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_idempotency_records_completed'",
                String.class);
        assertThat(completedCheck).contains("response_status", "response_body");
    }
}
