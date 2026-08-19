# FrameFlow

> 面向 3～30 人视频创作团队的 AI 视频项目协同与交付平台，同时作为 Java 后端、微服务治理与云原生工程实战项目。

## 浏览声明（使用许可）

本仓库**仅限浏览与学习参考**。保留所有权利，**禁止复制、修改、商用、二次分发或以本项目为模板开发竞品**。如需使用、合作或转载，请先联系项目所有者并获得明确书面许可。GitHub 上未附带 LICENSE 文件即表示默认保留所有权利，本声明是对该语义的明确化。

## 当前真实状态

- 当前仓库已完成 Git 基线并完成 **P0 工程基座**；**M01（用户/团队/认证）代码、测试与四条验收证据已完成并提交**（任务状态待调度器依据 Agent Receipt 回写 DONE）。
- 仓库模式：**A（源码单仓库）**——P0-Prep 门禁通过后，代码、docs/、deploy/ 与 evidence/ 将全部在本仓库内开发。
- P0-Prep 已于 2026-08-13 通过：五类机器门禁 37/37 项 PASS，原始结果在 `evidence/prep/`。P0 已获用户授权并完成 Git 基线，六条 P0 验收证据均已通过；P0 完成后停下，不自动进入 M01。
- `.zcode/` 按用户要求作为已忽略的本地工具目录保留；它不是项目权威事实源、产品源码或验收证据，也不进入正式交付包。
- 文档中的技术、测试、压测、Kubernetes、Istio 和 AI 能力，除非在 [`docs/09-delivery/evidence-index.md`](./docs/09-delivery/evidence-index.md) 有命令与原始结果，否则只能视为计划或设计。
- 开发派发以 [`docs/05-engineering/tasks/`](./docs/05-engineering/tasks/) 下的结构化任务包（JSON）为准，由多 Agent 调度工具执行。

## 项目要解决的问题

视频商业项目的 Brief、任务、素材版本、AI 结果、客户反馈、授权和最终交付通常分散在多个工具中。FrameFlow 建立一个可追踪的事实源：

```text
Brief 版本化
→ 分镜与任务协作
→ 素材不可变版本
→ 异步分析与 AI suggestion
→ 批注、审核与返工
→ 客户确认
→ 锁定交付版本与审计
```

AI 输出只能作为 `Suggestion`，经过人工确认后才能写入正式业务数据。

## 架构演进主线

```text
P0～M4-A     模块化单体基础（本地 StoragePort）
M4-B～M8     素材 MinIO + Redis/RabbitMQ/AI/审核交付
M9～M12      DDD、Outbox/Kafka、集成测试、压测与 JVM 实验
M13～M15     有边界的微服务与 Spring Cloud 服务治理
M16          Docker/Helm/Kubernetes 发布、排障、扩缩容与回滚
M16-G        Istio 金丝雀、mTLS、授权策略与故障注入
M17          可复现交付、证据索引和求职演示
```

MVP 先验证业务闭环，不提前为了技术名词拆分服务。微服务、Kubernetes 和 Istio 是后续工程演进目标，不是 MVP 的产品功能。

## 最终技术路线

### 模块化单体与 MVP

- JDK 17 唯一基线（本机环境；Spring Boot 3.4.x、Maven 均与 Java 17 匹配）
- Spring Security 6、JWT、BCrypt、RBAC
- MyBatis-Plus + XML、Flyway、PostgreSQL
- Redis、MinIO、RabbitMQ、Fake/可替换 AI Provider
- JUnit 5、Mockito、MockMvc、Testcontainers、ArchUnit
- OpenAPI/springdoc、Actuator、Micrometer、结构化日志
- Docker、Docker Compose、Linux、Git、GitHub Actions

### 微服务与平台化

- Spring Cloud Gateway、Nacos（仅微服务 Compose 训练环境）
- OpenFeign、Spring Cloud LoadBalancer、Resilience4j
- OpenTelemetry、Prometheus、Grafana、Jaeger/Tempo
- Fluent Bit、Elasticsearch、Kibana
- MySQL 仅用于后续 `identity-service`，不进入 MVP，不做双写
- Docker 多阶段镜像、kind/minikube、Helm、Kubernetes、Nginx Ingress
- Istio、VirtualService、DestinationRule、mTLS、AuthorizationPolicy
- k6、JFR、jcmd、jstack、GC 日志、async-profiler

### 明确不放入主项目

- Spring Boot 2
- 若依二开或若依源码
- RocketMQ
- 与业务无关的手写 Netty 服务
- MySQL/PostgreSQL 双写、共享业务表或跨库事务炫技
- 没有业务问题和证据的十几个微服务、CQRS、Event Sourcing

## 模块化单体模块

```text
identity      用户、团队、成员、认证与授权
project       客户、项目、Brief 与版本
workflow      分镜、任务、评论与审核
asset         素材、不可变素材版本与授权
ai            分析任务、Provider Adapter 与 Suggestion
delivery      交付包、交付确认与版本锁定
governance    审计、通知与跨模块技术治理
```

模块禁止直接访问其他模块的 Repository 或数据库表；跨模块通过应用服务、Port 或领域事件协作。微服务阶段第一批只拆：

```text
gateway-service
identity-service       MySQL
project-service        PostgreSQL
asset-workflow-service PostgreSQL
```

## 文档入口

### 产品与架构

- [产品需求书](./docs/01-product/FrameFlow-PRD.md)
- [概要设计](./docs/02-architecture/FrameFlow-概要设计.md)
- [详细设计](./docs/03-data/FrameFlow-详细设计.md)
- [模块边界与依赖规则](./docs/02-architecture/module-boundaries-and-dependency-rules.md)
- [架构演进路线](./docs/02-architecture/architecture-evolution-roadmap.md)
- [服务边界与数据所有权](./docs/02-architecture/service-boundaries-and-data-ownership.md)
- [部署拓扑](./docs/02-architecture/deployment-topology.md)
- [Istio 流量与安全设计](./docs/02-architecture/istio-traffic-and-security-design.md)

### 工程执行（供多 Agent 调度工具使用）

- [总控开发规范](./docs/05-engineering/master-control-spec.md)
- [任务包规范](./docs/05-engineering/task-capsule-spec.md)
- [任务包目录（可执行清单）](./docs/05-engineering/task-capsule-catalog.md)
- [P0～M17 开发计划](./docs/05-engineering/development-plan-p0-m17.md)
- [AI 开发 Prompt 入口](./docs/05-engineering/全量AI开发提示词脚本.md)

### API、工程与测试

- [OpenAPI v1（M01 唯一权威契约）](./docs/04-api/openapi/frameflow-v1.yaml)
- [API 规范](./docs/04-api/api-guidelines.md)
- [错误码注册表](./docs/04-api/error-code-catalog.md)
- [幂等契约](./docs/04-api/idempotency-contract.md)
- [权限矩阵](./docs/04-api/permission-matrix.md)
- [Token 契约](./docs/04-api/token-contract.md)
- [服务契约](./docs/04-api/service-contracts.md)
- [消息目录（Command/Event/Result）](./docs/04-api/event-catalog.md)
- [身份数据字典](./docs/03-data/identity-data-dictionary.md)
- [本地开发约定](./docs/05-engineering/local-development.md)
- [微服务拆分计划](./docs/05-engineering/microservice-extraction-plan.md)
- [测试策略](./docs/06-testing/test-strategy.md)
- [验收矩阵](./docs/06-testing/acceptance-matrix.md)

### 运维与交付

- [安全基线](./docs/07-operations/security-baseline.md)
- [故障响应](./docs/07-operations/incident-response.md)
- [Kubernetes Runbook](./docs/07-operations/kubernetes-runbook.md)
- [Istio Runbook](./docs/07-operations/istio-runbook.md)
- [打包规范](./docs/00-governance/packaging-policy.md)
- [证据索引](./docs/09-delivery/evidence-index.md)
- [48 周学习与开发路线](./docs/08-learning/48-week-plan.md)
- [Agent 控制面设计（PROPOSAL）](./docs/05-engineering/agent-control/agent-control-plane.md)

## 运行方式

P0 已提供可运行的本地基座。默认只绑定本机地址，数据库使用 FrameFlow 独立 Compose project 和具名 volume：

```text
docker compose -f docker-compose.local.yml up -d --wait
./mvnw -B clean verify
java -jar frameflow-app/target/frameflow-app-0.1.0-SNAPSHOT.jar
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/readiness
docker compose -f docker-compose.local.yml stop
```

`/health` 返回 `200 {"status":"UP"}` 且不查询数据库；`/readiness` 在数据库可用时返回 `200 READY`，数据库停止时返回 `503 NOT_READY`。停止命令不会删除数据库 volume；清理 volume 只能作为明确的本地重置动作执行。六条 P0 原始验收结果登记在 `evidence/p0/`。

后续运行模式为：

| 模式 | 目的 | 主要依赖 |
|---|---|---|
| `local-monolith` | 模块化单体业务开发 | PostgreSQL |
| `local-worker` | 单体 + 异步分析闭环 | PostgreSQL、Redis、MinIO、RabbitMQ |
| `local-microservices` | Spring Cloud 服务治理训练 | Gateway、Nacos、MySQL、PostgreSQL、Redis、RabbitMQ |
| `local-k8s` | Kubernetes 发布、排障与回滚 | kind/minikube、Helm |
| `local-istio` | 灰度、mTLS、授权和故障注入 | Kubernetes、Istio |

## 证据规则

每个阶段必须同时记录代码、测试、命令、原始输出、环境和限制。没有证据时使用“计划/未验证”，不使用“已完成/生产级”。压测数字、JVM 结论、用户反馈和故障经历都不得编造。

## 研发隔离与安全红线

- FrameFlow 的研发控制面不是用户业务功能。
- 不复制或使用 `jcm_media_api` 的配置、密钥、地址、外部服务或测试环境。
- 不提交 `.env`、Token、API Key、真实客户数据、真实素材或完整预签名 URL。
- 所有开发使用 FrameFlow 自己的隔离本地环境。
- AI 输入需要脱敏；真实 Provider 只有在成本、超时、重试和人工确认边界完成后才接入。
- 详见 [文档治理规则](./docs/00-governance/documentation-policy.md) 和 [安全基线](./docs/07-operations/security-baseline.md)。
