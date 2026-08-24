package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.product.service.SemanticModelCatalogProperties;
import com.frameflow.learning.product.service.SemanticModelCatalogService;
import org.junit.jupiter.api.Test;

class SemanticModelCatalogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configured_catalog_projects_only_safe_fields() throws Exception {
        SemanticModelCatalogService catalog = catalog("""
                {"defaultModelId":"balanced","models":[
                  {"id":"balanced","label":"均衡模型","description":"默认档",
                   "provider":"openai-compat","model":"vendor-vision-balanced",
                   "baseUrl":"https://private.example/v1",
                   "apiKeyEnv":"FRAMEFLOW_MODEL_BALANCED_API_KEY","enabled":true},
                  {"id":"premium","label":"高质量模型","description":"暂未开放",
                   "provider":"openai-compat","model":"vendor-vision-premium",
                   "baseUrl":"https://premium.example/v1",
                   "apiKeyEnv":"FRAMEFLOW_MODEL_PREMIUM_API_KEY","enabled":false}
                ]}
                """);

        assertThat(catalog.defaultModelId()).isEqualTo("balanced");
        assertThat(catalog.isEnabled("balanced")).isTrue();
        assertThat(catalog.isEnabled("premium")).isFalse();
        assertThat(catalog.isEnabled("missing")).isFalse();

        String responseJson = objectMapper.writeValueAsString(catalog.safeCatalog());
        assertThat(responseJson)
                .contains("\"defaultModelId\":\"balanced\"")
                .contains("\"enabled\":false")
                .doesNotContain("baseUrl")
                .doesNotContain("apiKeyEnv")
                .doesNotContain("FRAMEFLOW_MODEL_BALANCED_API_KEY")
                .doesNotContain("private.example");
    }

    @Test
    void responses_provider_is_accepted_and_projects_only_safe_fields() throws Exception {
        SemanticModelCatalogService catalog = catalog("""
                {"defaultModelId":"responses-vision","models":[
                  {"id":"responses-vision","label":"Responses 视觉模型","description":"受控验证入口",
                   "provider":"openai-responses","model":"gpt-5.6-luna",
                   "baseUrl":"https://private-responses.example/v1",
                   "apiKeyEnv":"FRAMEFLOW_RESPONSES_API_KEY","enabled":true}
                ]}
                """);

        assertThat(catalog.defaultModelId()).isEqualTo("responses-vision");
        assertThat(catalog.isEnabled("responses-vision")).isTrue();
        assertThat(catalog.safeCatalog().models()).singleElement().satisfies(model -> {
            assertThat(model.provider()).isEqualTo("openai-responses");
            assertThat(model.model()).isEqualTo("gpt-5.6-luna");
            assertThat(model.enabled()).isTrue();
        });

        String responseJson = objectMapper.writeValueAsString(catalog.safeCatalog());
        assertThat(responseJson)
                .contains("\"provider\":\"openai-responses\"")
                .doesNotContain("baseUrl")
                .doesNotContain("apiKeyEnv")
                .doesNotContain("FRAMEFLOW_RESPONSES_API_KEY")
                .doesNotContain("private-responses.example");
    }

    @Test
    void empty_catalog_keeps_legacy_single_model_compatible() {
        SemanticModelCatalogService catalog = new SemanticModelCatalogService(
                new SemanticModelCatalogProperties("", "legacy-vision-model"), objectMapper);

        assertThat(catalog.defaultModelId()).isEqualTo("platform-default");
        assertThat(catalog.isEnabled("platform-default")).isTrue();
        assertThat(catalog.safeCatalog().models()).singleElement().satisfies(model -> {
            assertThat(model.id()).isEqualTo("platform-default");
            assertThat(model.provider()).isEqualTo("openai-compat");
            assertThat(model.model()).isEqualTo("legacy-vision-model");
            assertThat(model.enabled()).isTrue();
        });
    }

    @Test
    void invalid_or_secret_like_platform_config_fails_closed() {
        assertThatThrownBy(() -> catalog("""
                {"defaultModelId":"disabled","models":[
                  {"id":"disabled","label":"停用","description":"",
                   "provider":"openai-compat","model":"vision",
                   "baseUrl":"https://example.test/v1",
                   "apiKeyEnv":"FRAMEFLOW_DISABLED_KEY","enabled":false}
                ]}
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认模型必须启用");

        assertThatThrownBy(() -> catalog("""
                {"defaultModelId":"balanced","models":[
                  {"id":"balanced","label":"均衡","description":"",
                   "provider":"openai-compat","model":"vision",
                   "baseUrl":"https://example.test/v1",
                   "apiKeyEnv":"sk-live-secret","enabled":true}
                ]}
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须是环境变量名");

        assertThatThrownBy(() -> catalog("""
                {"defaultModelId":"balanced","models":[
                  {"id":"balanced","label":"均衡","description":"",
                   "provider":"unsupported-provider","model":"vision",
                   "baseUrl":"https://example.test/v1",
                   "apiKeyEnv":"FRAMEFLOW_BALANCED_KEY","enabled":true}
                ]}
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON 配置无效: "
                        + "$.models[0].provider 当前仅支持 "
                        + "openai-compat 或 openai-responses");

        assertThatThrownBy(() -> catalog("""
                {"defaultModelId":"balanced","models":[
                  {"id":"balanced","label":"均衡","description":"",
                   "provider":"openai-compat","model":"vision",
                   "baseUrl":"https://example.test/v1?api_key=secret",
                   "apiKeyEnv":"FRAMEFLOW_BALANCED_KEY","enabled":true}
                ]}
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("查询参数或片段");
    }

    private SemanticModelCatalogService catalog(String json) {
        return new SemanticModelCatalogService(
                new SemanticModelCatalogProperties(json, "legacy"), objectMapper);
    }
}
