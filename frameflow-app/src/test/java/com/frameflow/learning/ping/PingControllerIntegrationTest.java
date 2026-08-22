package com.frameflow.learning.ping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 集成测试：启动完整 Spring 上下文，用 MockMvc 发起（模拟的）HTTP 请求。
 * 验证的是整条链路：路由匹配 → 配置绑定 → Service → JSON 序列化。
 */
// ★ 核心：@SpringBootTest + @AutoConfigureMockMvc 与 @WebMvcTest 的取舍——
// 前者起整个应用（含配置绑定、全部 Bean），验证"真的能跑"，但慢；
// 后者只起 Web 层（Controller + 过滤器），快但覆盖面窄（Service 需 Mock）。
// 本测试要验证"配置真的被绑定进来了"，所以必须用完整上下文。
@SpringBootTest
@AutoConfigureMockMvc
class PingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ping_returns_200_with_expected_json_shape() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("frameflow-select"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.serverTime").isNotEmpty());
    }
}
