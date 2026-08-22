package com.frameflow.learning.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【F1-T1 阅读起点】GET /api/v1/ping → 应用名 / 版本 / 服务器时间。
 *
 * 阅读顺序：本类 → PingService → AppInfoProperties → application.yml。
 *
 * 一次请求的完整链路：HTTP 请求 → 内嵌 Tomcat → DispatcherServlet 按路径
 * 匹配到本类方法 → 返回 record → Jackson 序列化成 JSON → HTTP 响应。
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final PingService pingService;

    // ★ 核心：构造器注入（而不是字段上直接 @Autowired）——依赖必须显式给出、
    // 字段可以声明为 final（不可变），且脱离 Spring 容器也能直接 new 来做单元测试。
    // 改坏后果：改成字段注入后，PingServiceTest 就无法简单 new PingService(...)，
    // 只能把测试也绑到 Spring 容器上，测试变慢且耦合变深。
    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return pingService.buildPing();
    }
}
