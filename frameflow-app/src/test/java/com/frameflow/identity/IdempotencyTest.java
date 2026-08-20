package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-09 两条团队写接口的 PostgreSQL 幂等重放、冲突与并发唯一效果 */
class IdempotencyTest extends IdentityIntegrationTestBase {

    @Test
    void test09_postgresIdempotencyForTeamWrites() throws Exception {
        String ownerEmail = nextEmail();
        register(ownerEmail, "passw0rd!");
        Map ownerPair = login(ownerEmail, "passw0rd!");
        String ownerToken = accessTokenOf(ownerPair);

        // --- POST /teams ---
        String key1 = UUID.randomUUID().toString();
        Map body1 = Map.of("name", nextName());
        ResponseEntity<String> first = postJson("/api/v1/teams", body1, headersWithKey(ownerToken, key1));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long firstTeamId = ((Number) json(first.getBody()).get("id")).longValue();

        // 同键同 payload 重放 → 首次结果（201、同一 team id）
        ResponseEntity<String> replay = postJson("/api/v1/teams", body1, headersWithKey(ownerToken, key1));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) json(replay.getBody()).get("id")).longValue()).isEqualTo(firstTeamId);

        // 业务效果只一次：该用户创建的团队数（幂等记录 COMPLETED 仅一条）
        Integer teamCountForName = jdbc.queryForObject(
                "SELECT count(*) FROM teams WHERE name = ?", Integer.class, body1.get("name"));
        assertThat(teamCountForName).isEqualTo(1);
        Integer idemRows = jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_records WHERE idempotency_key = ?::uuid AND status = 'COMPLETED'",
                Integer.class, key1);
        assertThat(idemRows).isEqualTo(1);

        // 同键不同 payload → 409 IDEMPOTENCY_CONFLICT
        ResponseEntity<String> conflict = postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(ownerToken, key1));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(conflict.getBody()).get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");

        // 并发 20 个同键同 payload → 全部 201 且只产生一个团队
        String key2 = UUID.randomUUID().toString();
        Map body2 = Map.of("name", nextName());
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            ResponseEntity<String> r = postJson("/api/v1/teams", body2, headersWithKey(ownerToken, key2));
            return r.getStatusCode().value();
        };
        List<Future<Integer>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> codes = new java.util.ArrayList<>();
        for (Future<Integer> future : futures) {
            codes.add(future.get());
        }
        pool.shutdown();
        assertThat(codes).allMatch(c -> c == 201);
        Integer teams2 = jdbc.queryForObject(
                "SELECT count(*) FROM teams WHERE name = ?", Integer.class, body2.get("name"));
        assertThat(teams2).isEqualTo(1);
        Integer owners2 = jdbc.queryForObject(
                "SELECT count(*) FROM team_members tm JOIN teams t ON t.id = tm.team_id "
                        + "WHERE t.name = ? AND tm.role = 'OWNER'", Integer.class, body2.get("name"));
        assertThat(owners2).isEqualTo(1);

        // --- POST /teams/{teamId}/members ---
        String memberEmail = nextEmail();
        register(memberEmail, "passw0rd!");
        long teamId = firstTeamId;
        String key3 = UUID.randomUUID().toString();
        Map addBody = Map.of("email", memberEmail, "role", "REVIEWER");
        ResponseEntity<String> addFirst = postJson("/api/v1/teams/" + teamId + "/members", addBody,
                headersWithKey(ownerToken, key3));
        assertThat(addFirst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long memberRecordId = ((Number) json(addFirst.getBody()).get("id")).longValue();

        ResponseEntity<String> addReplay = postJson("/api/v1/teams/" + teamId + "/members", addBody,
                headersWithKey(ownerToken, key3));
        assertThat(addReplay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) json(addReplay.getBody()).get("id")).longValue()).isEqualTo(memberRecordId);

        ResponseEntity<String> addConflict = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", memberEmail, "role", "VIEWER"), headersWithKey(ownerToken, key3));
        assertThat(addConflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(addConflict.getBody()).get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");

        // 成员关系只产生一次
        Integer memberRows = jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = "
                        + "(SELECT id FROM users WHERE email = ?) AND status = 'ACTIVE'",
                Integer.class, teamId, memberEmail);
        assertThat(memberRows).isEqualTo(1);
    }

    private HttpHeaders headersWithKey(String token, String key) {
        HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}