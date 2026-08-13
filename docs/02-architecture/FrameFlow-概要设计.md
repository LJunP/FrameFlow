# FrameFlow 概要设计说明书

> 版本：0.2（求职版架构演进基线）  
> 架构策略：先模块化单体，后按真实边界演进为微服务，再进行 Kubernetes/Istio 工程实验。

## 1. 架构原则

```text
领域边界先于部署边界
模块边界必须可测试
服务拥有自己的数据
业务元数据与媒体二进制分离
版本不可变、引用可追溯
长耗时任务异步化
AI 输出默认 suggestion
最小权限、审计优先
先可测试的模块化单体，后微服务
技术引入必须由业务问题和证据驱动
```

## 2. 阶段拓扑

### 2.1 MVP 模块化单体

```text
Web/API Client
      ↓
FrameFlow Modular Monolith
 ├─ identity
 ├─ project
 ├─ workflow
 ├─ asset
 ├─ ai
 ├─ delivery
 └─ governance
      ↓
PostgreSQL
      ↓ M5～M8
Redis + MinIO + RabbitMQ
      ↓
Fake/AI Analysis Worker
```

P0～M4-A 只实现 Java/Spring Boot/PostgreSQL 基座和核心业务（本地 StoragePort）；MinIO 自 M4-B 首次引入，Redis、RabbitMQ 按 M5～M8 引入。MVP 不引入 MySQL、Kafka、Spring Cloud、Kubernetes 或 Istio。

### 2.2 求职版微服务拓扑

```text
Client
  ↓
Spring Cloud Gateway
  ↓
identity-service       → MySQL
project-service        → PostgreSQL
asset-workflow-service → PostgreSQL + MinIO
  ↓                         ↓
Redis                 RabbitMQ / Kafka
  ↓                         ↓
AI Worker / Consumers / Audit
```

第一批只拆四个部署单元。`identity-service` 使用 MySQL 是 M13 后的独立服务实战，不改变 MVP 阶段 PostgreSQL 唯一事务主库的决策。不做跨库双写、共享业务表或跨数据库外键。

### 2.3 Kubernetes/Istio 拓扑

```text
Nginx Ingress 或 Istio Gateway
          ↓
Spring Cloud Gateway
          ↓
Kubernetes Service DNS
          ↓
Deployments + Pods + Sidecars（Istio 阶段）
          ↓
独立数据库/消息/对象存储依赖
```

Kubernetes 基础阶段使用 Service DNS、ConfigMap、Secret 和应用层 Resilience4j；Istio 阶段增加 VirtualService、DestinationRule、mTLS 和 AuthorizationPolicy。运行环境职责见 [部署拓扑](./deployment-topology.md)。

## 3. 模块化单体内部边界

| 模块 | 职责 | 核心对象 | 未来服务（定稿） |
|---|---|---|---|
| identity | 用户、团队、成员、认证、会话和角色 | User、Team、TeamMember、RefreshTokenSession、IdentityIdempotencyRecord | identity-service |
| project | 客户、项目、Brief 及版本 | Client、Project、BriefVersion | project-service |
| workflow | 分镜、任务、评论、审核 | ShotTask、Review | 任务/评论→project-service；审核→asset-workflow-service |
| asset | 素材、版本、授权和对象键 | Asset、AssetVersion、License | asset-workflow-service |
| ai | 分析任务、Provider、Suggestion | AiTask、Suggestion | Worker（业务事实归 asset-workflow-service） |
| delivery | 交付包、固定版本、确认 | DeliveryPackage、DeliveryItem | asset-workflow-service |
| governance | 审计和通知 | AuditLog、Notification | 事件消费者 |

模块只能通过公开的 Application API、Port 或领域事件协作，不得直接引用其他模块 Repository、Mapper 或表。

## 4. DDD 限界上下文

| 上下文 | MVP 职责 | M9 深化点 | 后续部署候选 |
|---|---|---|---|
| Identity & Access | 用户、团队、角色、资源授权 | 权限策略和身份边界 | identity-service |
| Project Workspace | 客户、项目、Brief、成员 | 项目聚合和版本切换 | project-service |
| Production Workflow | 分镜、任务、评论、审核 | 状态机和应用服务 | 任务/评论→project-service；审核→asset-workflow-service |
| Asset Management | 素材、版本、引用、授权 | Asset 聚合、不可变版本 | asset-workflow-service |
| AI Suggestion | AI 任务、建议、成本、确认 | Provider 防腐层 | Worker |
| Delivery | 交付包、锁定版本、外部访问 | 授权和交付规则 | asset-workflow-service |
| Governance | 审计、通知、证据 | 领域事件消费者 | 后续消费者 |

## 5. 存储与中间件职责

| 技术 | 阶段 | 职责 | 禁止事项 |
|---|---|---|---|
| PostgreSQL | P0 起 | MVP 唯一事务主库；项目、任务、素材元数据、审核、交付和审计 | 与 MySQL 双写 |
| MySQL | M13 Identity | identity-service 的独立数据存储 | MVP 主库、跨库事务 |
| Redis | M5 | Cache-Aside、PostgreSQL 请求幂等加速、限流、短期状态 | 作为业务或幂等事实唯一来源、滥用分布式锁 |
| MinIO | M4/M5 | 原始文件、预览、缩略图、交付包 | 让应用中转大文件 |
| RabbitMQ | M6 | 待执行任务、重试、DLQ、人工重放 | 代替领域事件事实 |
| Kafka | M11 | 已发生领域事件、多消费者、回放和分析 | MVP 必需依赖 |
| Elasticsearch | M15 | 日志查询；未来可选搜索投影 | 交易主库、权限事实库 |

## 6. 异步与一致性

### RabbitMQ：命令/任务

```text
创建 AssetVersion
→ AiTask
→ analysis.requested
→ RabbitMQ Consumer 手动 ACK
→ Suggestion / 状态更新
→ 临时失败重试
→ 永久失败 DLQ
```

必须实现 Publisher Confirm、持久化消息、手动 ACK/NACK、有限重试、幂等消费、人工重放和安全日志。

### Kafka：领域事件（M11 后）

```text
业务事务写业务表 + OutboxEvent
→ Publisher 发布 Kafka
→ 多消费者按 eventId 去重
→ offset 提交
→ 重试 Topic / DLT / 重放
```

Outbox 表结构、Outbox 机制启用和 Kafka Publisher 上线是三个不同状态；不得因为表预留就声称可靠事件链路已实现。

## 7. 安全、审计与可观测性

- Spring Security 6 + RS256 JWT + BCrypt；MVP 应用 Security Filter 与业务模块都必须校验身份和资源归属。
- RBAC 分层：团队角色为 `OWNER / PRODUCER / EDITOR / VIEWER`；`CLIENT` 是 M02 起的项目级外部角色，不进入 `team_members.role`。
- 所有资源通过组织/项目归属校验。
- MinIO 使用短期预签名 URL，签发前校验权限。
- 密钥只通过环境变量、Docker Secret 或 Kubernetes Secret 注入。
- 关键状态变更、交付确认、AI 人工确认和管理员重放记录审计。
- 日志为结构化 JSON，包含 `requestId`、`traceId`、`spanId`、服务、环境和脱敏业务标识。
- Actuator/Micrometer 提供 HTTP、JVM、连接池、缓存、消息和业务指标。
- OpenTelemetry 负责跨服务 Trace；Prometheus/Grafana 负责指标；Fluent Bit/Elasticsearch/Kibana 负责日志检索。

## 8. 运行环境职责矩阵

| 环境 | 服务发现 | 配置 | 外部入口 | 服务间治理 |
|---|---|---|---|---|
| `local-monolith` | 不需要 | Spring Profile/环境变量 | 应用自身 | 无 |
| `local-microservices` | Nacos | Nacos 非敏感配置 + Secret | Spring Cloud Gateway | Feign + Resilience4j |
| `local-k8s` | Kubernetes Service DNS | ConfigMap/Secret | Nginx Ingress + Gateway | 应用层超时/熔断 |
| `local-istio` | Kubernetes Service DNS | ConfigMap/Secret | Istio Gateway + Gateway | Istio 路由、mTLS、授权 |

Kubernetes 阶段不再把 Nacos 当作必要服务发现；应用重试与 Istio 重试必须明确单一主责，避免重试放大。

## 9. 渐进式演进与门禁

### P0～M8

进入条件：文档事实源和 P0 门禁通过。退出证据：模块化单体可运行、MVP E2E、权限/事务/版本/消息测试。

### M9～M12

进入条件：MVP 可演示且基础设施集成测试稳定。退出证据：DDD、Outbox/Kafka（若启用）、压测、JFR/jstack/GC 和故障复盘。

### M13～M15

进入条件：模块依赖测试通过、服务候选有独立部署/扩缩容/故障隔离理由。退出证据：服务数据所有权、迁移/回滚、契约、超时/熔断/降级、跨服务 Trace。

### M16

进入条件：服务镜像可重复构建、配置和健康检查稳定。退出证据：Kubernetes 部署、探针、资源、滚动发布、故障排查和回滚。

### M16-G

进入条件：Kubernetes 基础发布通过。退出证据：v1/v2 灰度、mTLS、AuthorizationPolicy、故障注入、指标和回滚。

## 10. ADR 索引

- ADR-001：模块化单体优先。
- ADR-002：PostgreSQL 作为 MVP 事务主库。
- ADR-003：MinIO 保存媒体，数据库保存元数据。
- ADR-004：RabbitMQ 负责待执行任务，Kafka 延后负责领域事件。
- ADR-005：AI 输出只作为 suggestion。
- ADR-006：素材版本不可变，交付引用固定版本。
- ADR-007：AI Provider 使用 Adapter，输出必须人工确认。
- ADR-008：微服务拆分门禁与首批服务边界。
- ADR-009：服务数据所有权与禁止共享数据库。
- ADR-010：API 与领域事件兼容策略。
- ADR-011：运行环境服务发现、配置与入口策略。

## 11. 待确认项

- 真实 AI Provider、回调签名和成本字段；
- 文件预览、转码和存储预算；
- 外部交付链接、水印和下载限制；
- 前端框架与 OpenAPI 代码生成方式；
- 真实试用反馈和多租户深化；
- OpenTelemetry Trace 后端最终选择 Jaeger 或 Tempo。
