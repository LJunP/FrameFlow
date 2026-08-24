package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SemanticModelCatalogIntegrationTest {

    private static final String CATALOG_JSON = """
            {"defaultModelId":"balanced","models":[
              {"id":"balanced","label":"均衡模型","description":"推荐默认档",
               "provider":"openai-compat","model":"vendor-balanced",
               "baseUrl":"https://private-balanced.example/v1",
               "apiKeyEnv":"FRAMEFLOW_MODEL_BALANCED_API_KEY","enabled":true},
              {"id":"premium","label":"高质量模型","description":"暂未开放",
               "provider":"openai-compat","model":"vendor-premium",
               "baseUrl":"https://private-premium.example/v1",
               "apiKeyEnv":"FRAMEFLOW_MODEL_PREMIUM_API_KEY","enabled":false}
            ]}
            """;

    @DynamicPropertySource
    static void semanticCatalog(DynamicPropertyRegistry registry) {
        registry.add("frameflow.semantic.model-catalog-json", () -> CATALOG_JSON);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void catalog_api_is_protected_and_returns_exact_safe_projection() throws Exception {
        mockMvc.perform(get("/api/v1/semantic-models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        Map<String, Object> owner = registerOwner("model-catalog-api@example.com");
        MvcResult result = mockMvc.perform(get("/api/v1/semantic-models")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModelId").value("balanced"))
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.models[0].enabled").value(true))
                .andExpect(jsonPath("$.models[1].enabled").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("baseUrl")
                .doesNotContain("apiKeyEnv")
                .doesNotContain("FRAMEFLOW_MODEL_BALANCED_API_KEY")
                .doesNotContain("private-balanced.example");

        JsonNode json = objectMapper.readTree(body);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("defaultModelId", "models");
        for (JsonNode model : json.get("models")) {
            assertThat(fieldNames(model)).containsExactlyInAnyOrder(
                    "id", "label", "description", "provider", "model", "enabled");
        }
    }

    @Test
    void profile_accepts_enabled_model_rejects_others_and_freezes_default() throws Exception {
        Map<String, Object> owner = registerOwner("model-profile@example.com");

        long explicitId = createProfile(owner, "显式模型", """
                {"semantic":{"enabled":true,"modelId":"balanced"}}
                """);
        mockMvc.perform(get("/api/v1/quality-profiles/" + explicitId + "/versions/1")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.semantic.modelId").value("balanced"));

        assertCreateProfileInvalid(owner, "未知模型", """
                {"semantic":{"enabled":true,"modelId":"missing"}}
                """);
        assertCreateProfileInvalid(owner, "停用模型", """
                {"semantic":{"enabled":true,"modelId":"premium"}}
                """);

        long defaultedId = createProfile(owner, "兼容缺省模型", """
                {"semantic":{"enabled":true,"dimensions":["prompt_alignment"]}}
                """);
        mockMvc.perform(get("/api/v1/quality-profiles/" + defaultedId + "/versions/1")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.semantic.modelId").value("balanced"));

        String storedSpec = jdbcTemplate.queryForObject(
                "SELECT spec::text FROM quality_profile_versions "
                        + "WHERE profile_id = ? AND version_no = 1",
                String.class, defaultedId);
        assertThat(objectMapper.readTree(storedSpec)
                .at("/semantic/modelId").textValue()).isEqualTo("balanced");

        mockMvc.perform(post("/api/v1/quality-profiles/" + defaultedId + "/versions")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "spec", "{\"semantic\":{\"enabled\":true,"
                                        + "\"modelId\":\"premium\"}}"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPEC"));
    }

    @Test
    void existing_legacy_profile_without_model_id_is_not_rewritten_on_read() throws Exception {
        Map<String, Object> owner = registerOwner("legacy-model-profile@example.com");
        long profileId = createProfile(owner, "历史 Profile 模拟", """
                {"semantic":{"enabled":true}}
                """);

        // 模拟 F6.1 上线前已经存在的不可变版本：新入口会固化默认值，历史行不会。
        jdbcTemplate.update(
                "UPDATE quality_profile_versions "
                        + "SET spec = '{\"semantic\":{\"enabled\":true}}'::jsonb "
                        + "WHERE profile_id = ? AND version_no = 1",
                profileId);

        mockMvc.perform(get("/api/v1/quality-profiles/" + profileId + "/versions/1")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.semantic.enabled").value(true))
                .andExpect(jsonPath("$.spec.semantic.modelId").doesNotExist());

        String storedModelId = jdbcTemplate.queryForObject(
                "SELECT spec -> 'semantic' ->> 'modelId' "
                        + "FROM quality_profile_versions WHERE profile_id = ? AND version_no = 1",
                String.class, profileId);
        assertThat(storedModelId).isNull();
    }

    private Map<String, Object> registerOwner(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                                + "\"displayName\":\"owner\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private long createProfile(Map<String, Object> tokens, String name, String spec) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", name, "spec", spec.strip()))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void assertCreateProfileInvalid(Map<String, Object> tokens, String name,
                                            String spec) throws Exception {
        mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", name, "spec", spec.strip()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPEC"));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
