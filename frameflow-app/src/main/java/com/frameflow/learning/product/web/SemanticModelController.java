package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.SemanticModelCatalogService;
import com.frameflow.learning.product.service.SemanticModelCatalogService.SafeCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已登录用户读取平台可选多模态模型；API Key 等私有路由配置不经过本接口。 */
@RestController
@RequestMapping("/api/v1/semantic-models")
public class SemanticModelController {

    private final SemanticModelCatalogService catalog;

    public SemanticModelController(SemanticModelCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public SafeCatalog list() {
        // ★ 核心：只返回预先构造的 SafeCatalog，不能为了省映射直接序列化平台
        // 配置对象；否则 baseUrl、apiKeyEnv 乃至未来新增的密钥字段会泄露给浏览器。
        return catalog.safeCatalog();
    }
}
