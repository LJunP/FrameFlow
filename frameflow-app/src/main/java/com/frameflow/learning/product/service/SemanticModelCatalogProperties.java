package com.frameflow.learning.product.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 平台托管的多模态模型目录配置。 */
@ConfigurationProperties(prefix = "frameflow.semantic")
public record SemanticModelCatalogProperties(
        /** FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON 的原始 JSON；空值启用单模型兼容目录。 */
        @DefaultValue("") String modelCatalogJson,
        /** 旧单模型模式使用的 FRAMEFLOW_SEMANTIC_MODEL。 */
        @DefaultValue("gpt-4o-mini") String legacyModel) {
}
