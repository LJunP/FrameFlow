# 架构演进路线

## 1. 总览

```text
P0～M01-H   基座、身份功能与加固
M01-F        本仓库前端基座与认证
M02～M04-A  项目/任务/本地素材核心
M04-F        核心业务前端
M04-B、M6～M8  可靠异步与交付 backend gate
M08-F        产品 MVP gate，之后真实用户验证
M05          可选 Redis hardening/engineering-lab
M9～M12      DDD、可靠事件、压测与 JVM
M13～M15     微服务与 Spring Cloud 治理
M16          Docker/Helm/Kubernetes
M16-G        Istio 灰度与服务间安全
M17          证据和求职交付
```

技术不是阶段目标本身。每个阶段必须先有业务问题，再有最小实现、测试、故障实验、指标观察和复盘。

## 2. 阶段门禁

| 阶段 | 进入条件 | 主要实现 | 退出证据 | 禁止事项 |
|---|---|---|---|---|
| P0 | P0-Prep 当前态门禁通过，Git 初始化与 P0 执行均获用户授权 | Java 17、Spring Boot 3.4.x、PostgreSQL、Flyway、健康检查 | `mvn verify`、Compose、health/readiness、仓库卫生 | Redis、消息、微服务、K8s |
| M01-H + M02 Contract Gate | M01 功能已合并 | 安全/证据/架构加固；起草 M02 数据/API/权限/测试决策 | M01H Evidence/Receipt；M02 UNKNOWN 得到批准 | 提前实现 M02 或修改 docs/04 |
| M01-F | M01H DONE | `frameflow-web/` 基座、BFF、认证/团队界面 | lint/type/build/E2E、HttpOnly Cookie 安全边界 | 独立仓库、Refresh Token localStorage |
| M02～M04-A + M04-F | M02 Contract Gate 通过 | Project、Workflow、Asset 核心及对应前端 | 权限、事务、版本、API/E2E | 过早拆服务 |
| M04-B、M06～M08 + M08-F | 核心业务可用 | MinIO、RabbitMQ、AI suggestion、审核/交付、全栈界面 | M08 backend gate + M08-F product MVP gate | Kafka、Spring Cloud、跳过 M08-F 试用 |
| M05 | 业务事实源已由 PostgreSQL 保证 | 可选 Redis 缓存/限流/降级实验 | 一致性、故障回源、429 证据 | 阻塞 MVP |
| M9～M12 | MVP 可演示 | DDD、Outbox/Kafka、性能、JVM | 测试、压测、JFR、故障复盘 | 无理由多库/多服务 |
| M13～M15 | 模块边界测试通过 | 四个以内服务、Gateway、Feign、治理、Trace | 数据所有权、契约、熔断、跨服务 Trace | 共享数据库 |
| M16 | 镜像可复现 | Helm、K8s、Probe、资源、回滚、HPA | kubectl 输出、Runbook、回滚 | 生产容量宣称 |
| M16-G | K8s 基础通过 | Istio 灰度、mTLS、授权、故障注入 | 流量、安全和回滚证据 | 只提交 YAML |
| M17 | 全部阶段证据可追溯 | 演示、简历、面试材料 | Evidence Index 完整 | 编造生产经验 |

## 3. 技术引入顺序

```text
Spring Boot/PostgreSQL
→ Spring Security/JWT/业务事务
→ MyBatis-Plus/Flyway/架构测试
→ MinIO/RabbitMQ
→ 可选 Redis hardening（旁路，不阻塞主线）
→ DDD/Outbox/Kafka
→ k6/JFR/JVM
→ Gateway/Nacos/Feign/Resilience4j
→ OTel/Prometheus/Grafana/ELK
→ Docker/Helm/Kubernetes
→ Istio
```

## 4. 演进理由

- 先单体：验证领域边界，避免分布式复杂度掩盖业务错误。
- 后异步：素材分析和 AI 调用耗时且可重试，适合 RabbitMQ 任务。
- 后事件：只有多个消费者、回放或分析需求成立时，Kafka/Outbox 才增加价值。
- 后微服务：只有独立部署、扩缩容、故障隔离或数据所有权证据成立才拆分。
- 后云原生：先证明服务能正确运行，再证明能发布、排障、回滚和扩缩容。
- 后 Istio：只为灰度、mTLS 和流量治理等明确场景引入。

## 5. 面试主线

```text
为什么先模块化单体？
→ 如何证明模块边界？
→ 为什么 RabbitMQ 与 Kafka 分工不同？
→ 如何从单体迁移到独立服务？
→ 服务数据如何保持所有权？
→ 如何通过 Trace/日志/指标排查故障？
→ K8s 发布失败如何回滚？
→ Istio 灰度和应用层重试如何避免冲突？
```
