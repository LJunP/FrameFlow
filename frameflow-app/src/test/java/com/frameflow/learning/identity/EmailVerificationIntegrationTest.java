package com.frameflow.learning.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.TestcontainersConfiguration;
import com.frameflow.learning.identity.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailVerificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EmailVerificationService emailVerificationService;

    @Test
    void confirm_marks_user_verified() throws Exception {
        var tokens = register("verify-me@example.com", "vm");
        mockMvc.perform(post("/api/v1/auth/verify-email/request")
                        .header("Authorization", "Bearer " + tokens.get("accessToken")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/verify-email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"this-token-is-not-valid-xx\"}"))
                .andExpect(status().isBadRequest());
        long userId = ((Number) ((Map<?, ?>) tokens.get("user")).get("id")).longValue();
        String raw = emailVerificationService.requestForUser(userId).orElseThrow();
        mockMvc.perform(post("/api/v1/auth/verify-email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isNoContent());
        Boolean verified = jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, userId);
        org.assertj.core.api.Assertions.assertThat(verified).isTrue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "displayName", displayName, "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
