package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** There is no disable API yet; direct DB state changes verify the policy boundary. */
class ActiveUserPolicyIntegrationTest extends IdentityIntegrationTestBase {

    @Test
    void disabledUserCannotRefreshCreateTeamOrAddMember() {
        String ownerEmail = nextEmail();
        String targetEmail = nextEmail();
        register(ownerEmail, "passw0rd!");
        register(targetEmail, "passw0rd!");
        Map pair = login(ownerEmail, "passw0rd!");
        String access = accessTokenOf(pair);

        HttpHeaders createHeaders = bearerHeaders(access);
        createHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> created = postJson("/api/v1/teams", Map.of("name", nextName()), createHeaders);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long teamId = idOf(json(created.getBody()));

        jdbc.update("UPDATE users SET status='DISABLED' WHERE email=?", ownerEmail);

        ResponseEntity<String> refreshed = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshTokenOf(pair)), new HttpHeaders());
        assertUnauthorized(refreshed);

        HttpHeaders disabledCreateHeaders = bearerHeaders(access);
        disabledCreateHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        assertUnauthorized(postJson("/api/v1/teams", Map.of("name", nextName()), disabledCreateHeaders));

        HttpHeaders addHeaders = bearerHeaders(access);
        addHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        assertUnauthorized(postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", targetEmail, "role", "EDITOR"), addHeaders));
    }

    @Test
    void disabledTargetCannotBeAddedAsAnActiveMember() {
        String ownerEmail = nextEmail();
        String targetEmail = nextEmail();
        register(ownerEmail, "passw0rd!");
        register(targetEmail, "passw0rd!");
        String access = accessTokenOf(login(ownerEmail, "passw0rd!"));

        HttpHeaders createHeaders = bearerHeaders(access);
        createHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> created = postJson("/api/v1/teams", Map.of("name", nextName()), createHeaders);
        long teamId = idOf(json(created.getBody()));
        jdbc.update("UPDATE users SET status='DISABLED' WHERE email=?", targetEmail);

        HttpHeaders addHeaders = bearerHeaders(access);
        addHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> response = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", targetEmail, "role", "EDITOR"), addHeaders);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(response.getBody()).get("code")).isEqualTo("RESOURCE_NOT_FOUND");
    }

    private void assertUnauthorized(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(response.getBody()).get("code")).isEqualTo("AUTH_REQUIRED");
    }
}
