package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Real PostgreSQL races must converge to one winner and stable business 409 losers. */
class ConcurrencyConflictTest extends IdentityIntegrationTestBase {

    private static final int ATTEMPTS = 6;

    @Test
    void concurrentDuplicateRegistrationMapsEveryLoserToUserEmailConflict() throws Exception {
        String email = nextEmail();
        List<ResponseEntity<String>> responses = race(ATTEMPTS, () -> rest.postForEntity(
                url("/api/v1/auth/register"),
                Map.of("email", email, "password", "passw0rd!", "displayName", "Concurrent User"),
                String.class));

        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        List<ResponseEntity<String>> conflicts = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).toList();
        assertThat(conflicts).hasSize(ATTEMPTS - 1);
        assertThat(conflicts).allSatisfy(response ->
                assertThat(json(response.getBody()).get("code")).isEqualTo("USER_EMAIL_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void differentIdempotencyKeysRacingForSameMemberMapEveryLoserToMemberConflict() throws Exception {
        String ownerEmail = nextEmail();
        String targetEmail = nextEmail();
        register(ownerEmail, "passw0rd!");
        register(targetEmail, "passw0rd!");
        String access = accessTokenOf(login(ownerEmail, "passw0rd!"));

        HttpHeaders createHeaders = bearerHeaders(access);
        createHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> created = postJson("/api/v1/teams", Map.of("name", nextName()), createHeaders);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long teamId = idOf(json(created.getBody()));

        List<ResponseEntity<String>> responses = race(ATTEMPTS, () -> {
            HttpHeaders headers = bearerHeaders(access);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            return postJson("/api/v1/teams/" + teamId + "/members",
                    Map.of("email", targetEmail, "role", "EDITOR"), headers);
        });

        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        List<ResponseEntity<String>> conflicts = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).toList();
        assertThat(conflicts).hasSize(ATTEMPTS - 1);
        assertThat(conflicts).allSatisfy(response ->
                assertThat(json(response.getBody()).get("code")).isEqualTo("TEAM_MEMBER_ALREADY_EXISTS"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM team_members tm JOIN users u ON u.id=tm.user_id "
                        + "WHERE tm.team_id=? AND u.email=?",
                Integer.class, teamId, targetEmail)).isEqualTo(1);
    }

    private static <T> List<T> race(int attempts, ThrowingSupplier<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.get();
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
