package com.frameflow.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Global HTTP-contract metadata implemented once for every application response. */
@Configuration
public class OpenApiContractConfig {

    private static final String REQUEST_ID_REF = "#/components/headers/RequestId";

    @Bean
    OpenApiCustomizer frameFlowOpenApiContractCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
            components.addHeaders("RequestId", new Header()
                    .description("服务器为本次 HTTP 请求生成的请求 ID")
                    .required(true)
                    .schema(new StringSchema().format("uuid")));
            components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
            openApi.setComponents(components);

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() != null) {
                    operation.getResponses().values().forEach(response ->
                            response.addHeaderObject("X-Request-Id", new Header().$ref(REQUEST_ID_REF)));
                }
            }));
        };
    }
}
