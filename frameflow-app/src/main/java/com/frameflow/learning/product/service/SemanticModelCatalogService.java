package com.frameflow.learning.product.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 平台多模态模型目录：启动时校验私有路由配置，对产品接口只投影安全字段。
 *
 * 【F6.1 阅读顺序】application.yml(frameflow.semantic) → 本类
 * → SemanticModelController → QualityProfileService.validateSemantic。
 */
@Service
public class SemanticModelCatalogService {

    private static final Set<String> ROOT_FIELDS = Set.of("defaultModelId", "models");
    private static final Set<String> MODEL_FIELDS = Set.of(
            "id", "label", "description", "provider", "model",
            "baseUrl", "apiKeyEnv", "enabled");
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "openai-compat", "openai-responses");
    private static final Pattern MODEL_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ENV_NAME = Pattern.compile("[A-Z_][A-Z0-9_]*");
    private static final String FALLBACK_ID = "platform-default";
    private static final String FALLBACK_MODEL = "gpt-4o-mini";

    private final Catalog catalog;
    private final SafeCatalog safeCatalog;

    public SemanticModelCatalogService(SemanticModelCatalogProperties properties,
                                       ObjectMapper objectMapper) {
        this.catalog = parse(properties, objectMapper);
        this.safeCatalog = new SafeCatalog(catalog.defaultModelId(), catalog.models().stream()
                .map(model -> new SafeModel(model.id(), model.label(), model.description(),
                        model.provider(), model.model(), model.enabled()))
                .toList());
    }

    /** 用户可见目录。返回类型从结构上就不含路由地址、Key 环境变量名或 Key。 */
    public SafeCatalog safeCatalog() {
        return safeCatalog;
    }

    public String defaultModelId() {
        return catalog.defaultModelId();
    }

    public boolean isEnabled(String modelId) {
        return catalog.models().stream()
                .anyMatch(model -> model.enabled() && model.id().equals(modelId));
    }

    private Catalog parse(SemanticModelCatalogProperties properties, ObjectMapper objectMapper) {
        String raw = properties.modelCatalogJson();
        if (!StringUtils.hasText(raw)) {
            String model = StringUtils.hasText(properties.legacyModel())
                    ? properties.legacyModel().trim() : FALLBACK_MODEL;
            ConfiguredModel fallback = new ConfiguredModel(
                    FALLBACK_ID,
                    "平台默认模型",
                    "兼容既有单模型配置",
                    "openai-compat",
                    model,
                    "",
                    "FRAMEFLOW_SEMANTIC_API_KEY",
                    true);
            return new Catalog(FALLBACK_ID, List.of(fallback));
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            requireObject(root, "$", ROOT_FIELDS);
            String defaultModelId = requireText(root.get("defaultModelId"), "$.defaultModelId");
            JsonNode modelsNode = root.get("models");
            if (modelsNode == null || !modelsNode.isArray() || modelsNode.isEmpty()) {
                throw invalid("$.models", "必须是非空数组");
            }

            List<ConfiguredModel> models = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < modelsNode.size(); i++) {
                String path = "$.models[" + i + "]";
                JsonNode node = modelsNode.get(i);
                requireObject(node, path, MODEL_FIELDS);
                ConfiguredModel model = parseModel(node, path);
                if (!ids.add(model.id())) {
                    throw invalid(path + ".id", "模型 ID 不得重复");
                }
                models.add(model);
            }

            ConfiguredModel defaultModel = models.stream()
                    .filter(model -> model.id().equals(defaultModelId))
                    .findFirst()
                    .orElseThrow(() -> invalid("$.defaultModelId", "未指向目录中的模型"));
            if (!defaultModel.enabled()) {
                throw invalid("$.defaultModelId", "默认模型必须启用");
            }
            return new Catalog(defaultModelId, List.copyOf(models));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // ★ 核心：平台目录是安全与路由配置，损坏时必须启动失败；若静默退回
            // 默认模型，用户选择会被悄悄忽略，并可能把媒体发送到错误供应商。
            // 异常消息只给 JSON 路径，不回显原始配置，避免日志泄露敏感地址。
            throw invalid("$", "不是合法 JSON");
        }
    }

    private ConfiguredModel parseModel(JsonNode node, String path) {
        String id = requireText(node.get("id"), path + ".id");
        if (!MODEL_ID.matcher(id).matches()) {
            throw invalid(path + ".id", "仅允许小写字母、数字、点、下划线和短横线，最长 64 位");
        }
        String label = requireText(node.get("label"), path + ".label");
        String description = requireString(node.get("description"), path + ".description");
        String provider = requireText(node.get("provider"), path + ".provider");
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            // ★ 核心：Java 目录和 Worker 路由必须使用同一白名单；否则接口会把一个
            // Worker 必然拒绝的模型展示成可选项，直到任务运行时才失败。
            throw invalid(path + ".provider",
                    "当前仅支持 openai-compat 或 openai-responses");
        }
        String model = requireText(node.get("model"), path + ".model");
        String baseUrl = requireText(node.get("baseUrl"), path + ".baseUrl");
        validateBaseUrl(baseUrl, path + ".baseUrl");
        String apiKeyEnv = requireText(node.get("apiKeyEnv"), path + ".apiKeyEnv");
        if (!ENV_NAME.matcher(apiKeyEnv).matches()) {
            throw invalid(path + ".apiKeyEnv", "必须是环境变量名，不能直接填写密钥");
        }
        JsonNode enabledNode = node.get("enabled");
        if (enabledNode == null || !enabledNode.isBoolean()) {
            throw invalid(path + ".enabled", "必须是布尔值");
        }
        return new ConfiguredModel(id, label, description, provider, model,
                baseUrl, apiKeyEnv, enabledNode.booleanValue());
    }

    private void validateBaseUrl(String value, String path) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                // Query/fragment 既不属于 Chat Completions 根地址，也常被误用来
                // 携带 token；允许它们会绕过“目录不承载密钥”的结构约束。
                throw invalid(path, "必须是无用户信息、查询参数或片段的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            throw invalid(path, "必须是合法 URL");
        }
    }

    private void requireObject(JsonNode node, String path, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw invalid(path, "必须是对象");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw invalid(path + "." + field, "未知字段");
            }
        });
    }

    private String requireText(JsonNode node, String path) {
        String value = requireString(node, path).trim();
        if (value.isEmpty()) {
            throw invalid(path, "不能为空");
        }
        return value;
    }

    private String requireString(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw invalid(path, "必须是字符串");
        }
        return node.textValue();
    }

    private IllegalStateException invalid(String path, String reason) {
        return new IllegalStateException(
                "FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON 配置无效: " + path + " " + reason);
    }

    /** API 安全投影：新增字段时也不应把 ConfiguredModel 直接暴露出去。 */
    public record SafeCatalog(String defaultModelId, List<SafeModel> models) {
    }

    public record SafeModel(String id, String label, String description,
                            String provider, String model, boolean enabled) {
    }

    private record Catalog(String defaultModelId, List<ConfiguredModel> models) {
    }

    /** 完整平台配置只在服务内部存在，绝不能作为 Controller 返回值。 */
    private record ConfiguredModel(String id, String label, String description,
                                   String provider, String model, String baseUrl,
                                   String apiKeyEnv, boolean enabled) {
    }
}
