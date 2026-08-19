package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TEST-FF-M01-001-07 身份表迁移验证（V2 五表结构与约束；幂等重跑由 scripts/m01/check-migration.sh 验证） */
class MigrationTest extends IdentityIntegrationTestBase {

    @Test
    void test07_identityTablesMigration() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
        assertThat(tables).contains("users", "teams", "team_members", "refresh_token_sessions", "idempotency_records");

        // users 唯一邮箱
        Integer emailUq = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_users_email' AND contype = 'u'",
                Integer.class);
        assertThat(emailUq).isEqualTo(1);

        // team_members 唯一(team_id, user_id)
        Integer memberUq = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_team_members_team_user' AND contype = 'u'",
                Integer.class);
        assertThat(memberUq).isEqualTo(1);

        // refresh_token_sessions 唯一 token_hash
        Integer hashUq = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_refresh_token_sessions_token_hash' AND contype = 'u'",
                Integer.class);
        assertThat(hashUq).isEqualTo(1);

        // idempotency_records 唯一(scope, idempotency_key)
        Integer idemUq = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_idempotency_records_scope_key' AND contype = 'u'",
                Integer.class);
        assertThat(idemUq).isEqualTo(1);

        // V1 基线仍在（P0 迁移未被破坏）
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM frameflow_schema_baseline WHERE id = 1", Integer.class);
        assertThat(baseline).isEqualTo(1);

        // flyway 记录 V1 与 V2 均成功
        Integer v2Count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true AND version = '2'",
                Integer.class);
        assertThat(v2Count).isEqualTo(1);
    }
}
