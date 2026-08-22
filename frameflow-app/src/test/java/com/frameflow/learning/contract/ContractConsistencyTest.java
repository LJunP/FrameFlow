package com.frameflow.learning.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.frameflow.learning.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * T4 契约一致性测试：手写契约（docs/api/frameflow-v1.yaml）是唯一权威，
 * 运行时（springdoc 生成的 /v3/api-docs）必须与它双向一致：
 * - 契约里的每个 (路径, 方法, 成功状态码) 运行时必须存在；
 * - 运行时的每个 /api/v1 接口必须已写入契约——新增接口不更新契约 = 测试红。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContractConsistencyTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("unchecked")
    void runtime_matches_handwritten_contract() throws Exception {
        // surefire 的工作目录是模块目录 frameflow-app，契约在仓库根的 docs/api 下
        Map<String, Object> contract = new Yaml()
                .load(Files.newInputStream(Path.of("..", "docs", "api", "frameflow-v1.yaml")));
        Map<String, Object> contractPaths = (Map<String, Object>) contract.get("paths");

        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode runtimePaths = objectMapper
                .readTree(result.getResponse().getContentAsString()).get("paths");

        // 方向一：契约 ⊆ 运行时
        for (Map.Entry<String, Object> pathEntry : contractPaths.entrySet()) {
            String path = pathEntry.getKey();
            org.assertj.core.api.Assertions.assertThat(runtimePaths.has(path))
                    .as("契约中的路径 %s 在运行时不存在（接口被删了？）", path).isTrue();
            Map<String, Object> contractOperations = (Map<String, Object>) pathEntry.getValue();
            for (Map.Entry<String, Object> op : contractOperations.entrySet()) {
                if (!HTTP_METHODS.contains(op.getKey())) {
                    continue;
                }
                JsonNode runtimeOp = runtimePaths.get(path).get(op.getKey());
                org.assertj.core.api.Assertions.assertThat(runtimeOp)
                        .as("契约中的 %s %s 在运行时不存在", path, op.getKey()).isNotNull();
                String expectedSuccess = successCodeOf((Map<String, Object>) op.getValue());
                org.assertj.core.api.Assertions.assertThat(runtimeOp.get("responses").has(expectedSuccess))
                        .as("%s %s 契约声明成功码 %s，运行时没有", path, op.getKey(), expectedSuccess).isTrue();
            }
        }

        // 方向二：运行时的 /api/v1 接口 ⊆ 契约（禁止未 documented 的接口）
        runtimePaths.fieldNames().forEachRemaining(path -> {
            if (!path.startsWith("/api/")) {
                return;
            }
            org.assertj.core.api.Assertions.assertThat(contractPaths.containsKey(path))
                    .as("运行时存在接口 %s 但契约没写——先更新契约再写代码", path).isTrue();
            runtimePaths.get(path).fieldNames().forEachRemaining(method -> {
                if (HTTP_METHODS.contains(method)) {
                    org.assertj.core.api.Assertions
                            .assertThat(((Map<String, Object>) contractPaths.get(path)).containsKey(method))
                            .as("运行时 %s %s 未写入契约", path, method).isTrue();
                }
            });
        });
    }

    /** 取契约操作声明的第一个 2xx 成功码。 */
    private String successCodeOf(Map<String, Object> operation) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        return responses.keySet().stream()
                .filter(code -> code.startsWith("2"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("契约操作缺少 2xx 响应声明"));
    }
}
