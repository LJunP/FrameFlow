package com.frameflow.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.TestcontainersConfiguration;
import com.frameflow.learning.identity.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 团队邀请全流程：创建 / 哈希存储 / 列表不回令牌 / 新用户接受 / 已有账号接受 /
 * 过期 / 撤销 / 越权 404→403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamInvitationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TokenService tokenService;

    @Test
    void owner_creates_invite_stores_hash_and_list_omits_token() throws Exception {
        var owner = register("invite-owner@example.com", "invite-owner");
        long teamId = teamIdOf(owner);

        MvcResult created = mockMvc.perform(post(invitesUrl(teamId))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new-member@example.com\",\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-member@example.com"))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(
                created.getResponse().getContentAsString(), Map.class);
        String token = (String) body.get("token");
        long inviteId = ((Number) body.get("id")).longValue();
        assertThat(token).hasSize(64);

        String stored = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM team_invitations WHERE id = ?", String.class, inviteId);
        assertThat(stored.trim()).isEqualTo(tokenService.sha256(token));
        assertThat(stored.trim()).isNotEqualTo(token);

        mockMvc.perform(get(invitesUrl(teamId)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inviteId))
                .andExpect(jsonPath("$[0].token").value(nullValue()))
                .andExpect(jsonPath("$[0].email").value("new-member@example.com"));
    }

    @Test
    void new_user_accepts_invite_and_reuse_is_rejected() throws Exception {
        var owner = register("accept-owner@example.com", "accept-owner");
        long teamId = teamIdOf(owner);
        String token = createInvite(owner, teamId, "fresh@example.com", "OPERATOR");

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Passw0rd!\",\"displayName\":\"fresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("fresh@example.com"))
                .andExpect(jsonPath("$.team.id").value(teamId))
                .andExpect(jsonPath("$.team.role").value("OPERATOR"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        Integer members = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM team_members m JOIN users u ON u.id = m.user_id "
                        + "WHERE m.team_id = ? AND u.email = ?",
                Integer.class, teamId, "fresh@example.com");
        assertThat(members).isEqualTo(1);

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void existing_user_must_confirm_password_then_session_stays_on_invited_team() throws Exception {
        var invitee = register("already@example.com", "already");
        var owner = register("host-owner@example.com", "host-owner");
        long hostTeam = teamIdOf(owner);
        String token = createInvite(owner, hostTeam, "already@example.com", "VIEWER");

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.id").value(hostTeam))
                .andExpect(jsonPath("$.team.role").value("VIEWER"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"already@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.id").value(hostTeam));
        assertThat(teamIdOf(invitee)).isNotEqualTo(hostTeam);
    }

    @Test
    void expired_or_revoked_invite_is_invalid() throws Exception {
        var owner = register("expire-owner@example.com", "expire-owner");
        long teamId = teamIdOf(owner);
        String expiredToken = createInvite(owner, teamId, "late@example.com", "REVIEWER");
        jdbcTemplate.update(
                "UPDATE team_invitations SET expires_at = now() - interval '1 minute' "
                        + "WHERE token_hash = ?",
                tokenService.sha256(expiredToken));
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + expiredToken + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));

        String liveToken = createInvite(owner, teamId, "revoked@example.com", "REVIEWER");
        long inviteId = jdbcTemplate.queryForObject(
                "SELECT id FROM team_invitations WHERE token_hash = ?",
                Long.class, tokenService.sha256(liveToken));
        mockMvc.perform(delete(invitesUrl(teamId) + "/" + inviteId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + liveToken + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void foreign_team_is_404_and_non_owner_is_403_and_owner_role_rejected() throws Exception {
        var ownerA = register("iso-a@example.com", "iso-a");
        var ownerB = register("iso-b@example.com", "iso-b");
        long teamA = teamIdOf(ownerA);
        long teamB = teamIdOf(ownerB);

        mockMvc.perform(get(invitesUrl(teamB)).header("Authorization", bearer(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post(invitesUrl(teamB))
                        .header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(invitesUrl(teamA))
                        .header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String token = createInvite(ownerA, teamA, "op-invite@example.com", "OPERATOR");
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
        var operator = login("op-invite@example.com");
        mockMvc.perform(get(invitesUrl(teamA)).header("Authorization", bearer(operator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unauthenticated_create_is_401() throws Exception {
        mockMvc.perform(post("/api/v1/teams/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String createInvite(Map<String, Object> owner, long teamId, String email, String role)
            throws Exception {
        MvcResult created = mockMvc.perform(post(invitesUrl(teamId))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(
                created.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    private Map<String, Object> register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "displayName", displayName, "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private Map<String, Object> login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private String invitesUrl(long teamId) {
        return "/api/v1/teams/" + teamId + "/invitations";
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private long teamIdOf(Map<String, Object> tokens) {
        Map<String, Object> team = (Map<String, Object>) tokens.get("team");
        return ((Number) team.get("id")).longValue();
    }
}
