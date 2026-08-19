package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TEST-FF-M01-001-14 V2 Identity 表集合与 project_members 排除边界 */
class V2SchemaBoundaryTest extends IdentityIntegrationTestBase {

    @Test
    void test14_v2SchemaBoundary() {
        // project_members 属于 M02 project 模块，不得出现在 V2
        List<String> projectTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name LIKE '%project%'",
                String.class);
        assertThat(projectTables).isEmpty();

        List<String> allTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
        assertThat(allTables).containsExactlyInAnyOrder(
                "frameflow_schema_baseline", "users", "teams", "team_members",
                "refresh_token_sessions", "idempotency_records", "flyway_schema_history");

        // team_members.role CHECK 只允许 OWNER/PRODUCER/EDITOR/VIEWER，不包含 CLIENT
        String roleCheck = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_team_members_role'",
                String.class);
        assertThat(roleCheck).contains("OWNER", "PRODUCER", "EDITOR", "VIEWER").doesNotContain("CLIENT");

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
