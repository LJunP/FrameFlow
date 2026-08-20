# FrameFlow AI 开发提示词入口

> 版本：0.3。本文只做 Prompt 导航和总控规则；产品、架构、数据、API、阶段和证据事实以对应权威文档为准。
> 任务包的派发与验收格式由 `master-control-spec.md`、`task-capsule-spec.md` 和 `task-capsule-catalog.md` 定义。

## 1. 使用顺序

```text
00-project-control
→ 01-modular-monolith
→ 02-engineering-hardening
→ 03-microservice-extraction
→ 04-kubernetes
→ 05-istio
→ 06-final-evidence
```

每次使用前先读取：

- `docs/00-governance/documentation-policy.md`
- `docs/00-governance/project-status.md`
- `README.md`
- 当前阶段对应的 PRD、概要设计、详细设计、API、测试和运维文档

## 2. 总控约束

```text
你是 FrameFlow 的受控开发代理。

1. 只在 FrameFlow 工作区内工作，不访问、复制、运行或引用 jcm_media_api。
2. 不读取或输出真实密钥、Token、客户资料、素材、内网地址或完整外部 AI payload。
3. 先核验仓库事实，再输出范围、非范围、文件变更、测试计划和风险。
4. 不把 PLAN/PROPOSAL 当作 FACT；没有命令和原始结果不得声明已完成。
5. 先修改权威文档和 ADR，再修改代码或派生文档。
6. 只修改当前阶段必要文件，不提前引入后续技术。
7. Controller 不直接写 SQL；模块不得访问其他模块 Repository/Mapper/Entity/表。
8. 所有关键业务规则、事务、权限、幂等、消息、补偿和安全边界必须有测试。
9. 完成后如实输出实际改动、命令、原始结果、未完成项、风险和下一阶段门禁。
10. 遇到 UNKNOWN 或不可逆操作先停止并请求决策，不自行猜测。
```

## 3. 技术阶段边界

| 阶段 | 允许技术 |
|---|---|
| P0～M4-A | Java 17、Maven、Spring Boot 3.4.x、Spring MVC、Spring Security/JWT、MyBatis-Plus/XML、Flyway、PostgreSQL、JUnit、Docker Compose |
| M4-B～M8 | MinIO（M4-B 首次）、Redis、RabbitMQ、Fake/可替换 AI Provider |
| M9～M12 | DDD、Outbox/Kafka、契约测试、k6、JFR、jcmd、jstack、GC 日志（Testcontainers 自首次引入起使用，M12-B 强化） |
| M13～M15 | Gateway、Nacos（Compose）、OpenFeign、LoadBalancer、Resilience4j、OTel、Prometheus/Grafana、Fluent Bit、Elasticsearch/Kibana |
| M16～M16-G | Docker、Helm、kind/minikube、Kubernetes、Nginx Ingress、Istio |
| M17 | 证据、演示、求职材料 |

MVP 的事务主库从 P0 起是 PostgreSQL。MySQL 只在 M13 的独立 `identity-service` 使用，不做双写。RabbitMQ 负责待执行任务，Kafka 负责 M11 后的已发生领域事件。

## 4. 阶段 Prompt

- [P0～M8 模块化单体](./prompts/01-modular-monolith.md)
- [M9～M12 工程强化](./prompts/02-engineering-hardening.md)
- [M13～M15 微服务拆分](./prompts/03-microservice-extraction.md)
- [M16 Kubernetes](./prompts/04-kubernetes.md)
- [M16-G Istio](./prompts/05-istio.md)
- [M17 最终证据](./prompts/06-final-evidence.md)

## 5. 统一完成定义

阶段只有在以下项目齐全时才能标记完成：

```text
代码
+ 迁移/配置
+ 自动化测试
+ 真实命令与原始输出
+ 故障/性能/部署证据（适用时）
+ 文档和证据索引
+ 未完成项与限制说明
```
