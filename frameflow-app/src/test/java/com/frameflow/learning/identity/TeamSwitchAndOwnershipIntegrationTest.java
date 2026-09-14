package com.frameflow.learning.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamSwitchAndOwnershipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void switch_team_and_transfer_owner() throws Exception {
        var first = register("switch-a@example.com", "a");
        var second = register("switch-b@example.com", "b");
        long teamA = teamIdOf(first);
        long teamB = teamIdOf(second);

        mockMvc.perform(get("/api/v1/me/teams").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamA));

        String inviteToken = objectMapper.readTree(mockMvc.perform(post("/api/v1/teams/" + teamB + "/invitations")
                        .header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"switch-a@example.com\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/teams").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/v1/me/current-team")
                        .header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + teamA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.id").value(teamA))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void owner_can_transfer_to_member() throws Exception {
        var owner = register("xfer-owner@example.com", "xo");
        long teamId = teamIdOf(owner);
        String token = objectMapper.readTree(mockMvc.perform(post("/api/v1/teams/" + teamId + "/invitations")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"xfer-op@example.com\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
        var accepted = objectMapper.readValue(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Passw0rd!\",\"displayName\":\"op\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Map.class);
        long opId = ((Number) ((Map<?, ?>) accepted.get("user")).get("id")).longValue();

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/owner")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + opId + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("unchecked")
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

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private long teamIdOf(Map<String, Object> tokens) {
        return ((Number) ((Map<String, Object>) tokens.get("team")).get("id")).longValue();
    }
}
