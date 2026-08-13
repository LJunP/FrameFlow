# FrameFlow 产品需求书（PRD）

> 版本：0.2（求职版架构演进基线）  
> 状态：MVP 决策已批准，平台化阶段为工程计划  
> 产品定位：面向 3～30 人视频创作团队、摄影工作室和小型品牌内容团队的 AI 视频项目协同与交付平台。

## 1. 问题与目标

视频商业项目通常散落在微信、飞书、网盘、表格、剪辑软件和多个 AI 工具中，导致 Brief、任务、素材版本、客户反馈、授权和交付状态无法形成统一事实源。

FrameFlow 要解决：

```text
需求分散 → 项目/Brief 版本化
制作状态不清 → 分镜任务与负责人
素材版本混乱 → 逻辑素材 + 不可变版本
AI 结果不可追溯 → suggestion、参数与人工确认
反馈散落 → 时间点批注、审核、返工
交付不可审计 → 锁定版本、交付包、确认记录
```

## 2. 目标用户与角色

| 角色 | 主要目标 | MVP 权限 |
|---|---|---|
| OWNER | 管理团队、项目、成员 | 团队及项目全管理 |
| PRODUCER | 推进项目、分派任务、交付 | 项目管理、成员分派、交付 |
| EDITOR | 完成制作任务、提交版本 | 自身任务、素材/版本、提交审核 |
| VIEWER | 查看项目进展 | 项目只读、可按授权评论 |
| CLIENT | 查看指定内容、反馈与确认 | 批注、要求修改、确认交付 |

角色层级：`OWNER / PRODUCER / EDITOR / VIEWER` 是团队成员角色；`CLIENT` 是 M02 起绑定到明确项目授权的外部角色，不进入团队成员角色枚举，也不获得团队管理权限。

## 3. MVP 范围

### 必做

```text
用户/团队/成员角色
客户、项目、Brief 与 Brief 版本
分镜/制作任务、评论、乐观锁
素材、不可变素材版本、上传与 MinIO 存储
项目/权限缓存、通用幂等和限流
RabbitMQ 异步素材分析任务
AI 摘要、分镜建议、标签 suggestion
审核、时间点批注、返工、交付、版本锁定
操作审计与基础通知
```

MVP 的事务主库统一为 PostgreSQL。MVP 不引入 MySQL、Kafka、Spring Cloud、Kubernetes 或 Istio；这些属于后续工程演进，不是 MVP 产品功能。

### 明确不做

```text
浏览器内视频剪辑器
自研视频模型或推理集群
支付、合同、供应商结算
复杂 CRM/ERP
自动外发客户邮件
MVP 全量微服务
完整工作流低代码引擎
```

## 4. 核心业务闭环

```text
客户 Brief
→ 创建项目与 Brief 版本
→ 拆分分镜和制作任务
→ 上传素材并创建版本
→ RabbitMQ 异步分析 / AI suggestion
→ 团队和客户时间点批注
→ 提交审核
→ 返工产生新版本
→ 客户确认
→ 锁定交付版本、创建交付记录
→ 归档与复盘
```

## 5. 核心业务规则

1. 项目属于一个团队，可关联一个客户。
2. 非项目成员不可读取或修改项目资源。
3. CLIENT 只能批注、要求修改、确认交付，不能修改内部制作任务。
4. 一个项目只能有一个当前 Brief。
5. 项目状态：`DRAFT → PLANNING → SHOOTING → EDITING → REVIEWING → DELIVERED → ARCHIVED`。
6. `DELIVERED` 不能直接删除；`ARCHIVED` 默认只读。
7. 素材版本不可原地覆盖；审核通过后修改必须创建新版本并重新审核。
8. 交付包固定引用素材版本；交付锁定版本不可覆盖。
9. 授权过期素材不得进入新的交付包。
10. AI 输出只保存为 suggestion，必须人工确认才写入正式业务数据。
11. 交付确认必须幂等并记录审计。

## 6. 核心状态

| 对象 | 状态 |
|---|---|
| 任务 | `TODO / IN_PROGRESS / BLOCKED / DONE / CANCELLED` |
| 审核 | `PENDING_REVIEW / CHANGES_REQUESTED / APPROVED / REJECTED` |
| 素材版本 | `UPLOADED / ANALYZING / READY / IN_REVIEW / LOCKED_FOR_DELIVERY / ARCHIVED` |
| 项目 | `DRAFT / PLANNING / SHOOTING / EDITING / REVIEWING / DELIVERED / ARCHIVED` |

## 7. 分阶段产品与工程演进

> 轨道划分（DECISION）：`product-mainline`（P0～M8）是产品主线，不可跳过，M8 后做真实用户验证；`engineering-lab`（M9～M17）是工程实验轨道，按学习和演示目标选择执行，不阻塞产品主线。技术引入必须由业务问题或明确的实验目标驱动。

| 阶段 | 轨道 | 产品/工程目标 | 技术重点 | 不是当前阶段的内容 |
|---|---|---|---|---|
| P0～M4 | mainline | 可运行模块化单体和核心业务 | Java 17、Spring Boot 3.4.x、PostgreSQL、MyBatis-Plus、Flyway | Redis、消息、微服务、K8s |
| M4-B～M8 | mainline | MVP 协作、素材、AI、审核、交付闭环 | MinIO（M4-B 首次）、Redis、RabbitMQ、Fake/Provider Adapter | Kafka、微服务、K8s |
| M9～M12 | lab | 领域规则和可靠性成熟 | DDD、Outbox/Kafka、Testcontainers、压测、JVM | 过早拆分服务 |
| M13～M15 | lab | 服务独立部署和运行治理 | Gateway、Nacos（Compose）、Feign、Resilience4j、OTel、Prometheus/Grafana | 十几个微服务 |
| M16 | lab | 本地云原生平台实践 | Docker、Helm、kind/minikube、Kubernetes、Nginx Ingress | 生产集群宣称 |
| M16-G | lab | 受限服务网格实验 | Istio 灰度、mTLS、AuthorizationPolicy、故障注入 | 无业务目的的全量 Mesh |
| M17 | lab | 求职交付 | 证据索引、架构演示、故障复盘、面试材料 | 编造生产经历 |

## 8. 微服务演进边界

微服务拆分属于 **engineering-lab 轨道**。只有同时具备独立部署、独立扩缩容、故障隔离或数据所有权理由时才拆服务。第一批服务固定为：

```text
gateway-service
identity-service
project-service
asset-workflow-service
```

- `identity-service` 在 M13 使用 MySQL：这是工程实验轨道的技术目标（异构数据库/数据所有权训练），不是产品 MVP 架构理由；MVP 唯一事务主库始终是 PostgreSQL。
- `project-service` 和 `asset-workflow-service` 使用 PostgreSQL，各自拥有自己的数据。
- 不做共享业务表、跨库外键、MySQL/PostgreSQL 双写或为了简历制造分布式事务。
- AI Worker 可以独立部署，但没有必要先拆成独立 HTTP 业务服务；`ai_tasks`/`suggestions` 业务事实由发起分析的业务服务拥有。
- 服务归属定稿见 `service-boundaries-and-data-ownership.md` 与 ADR-008。

## 9. Elasticsearch 边界

Elasticsearch 不属于 MVP 事实库。平台阶段首先用于：

```text
结构化 JSON 日志
→ Fluent Bit
→ Elasticsearch
→ Kibana 查询和聚合
```

未来只有在出现跨字段全文、中文分词、字幕/AI 摘要检索或规模证据时，才增加素材搜索投影。项目权限、交付锁定和业务状态永远以 PostgreSQL 为准。

## 10. 验收

### MVP 验收

```text
团队成员可创建项目、Brief、任务
→ 上传素材并形成版本
→ 异步分析生成 suggestion
→ 进行批注和审核
→ 返工形成新版本
→ 客户确认交付
→ 交付版本被锁定、可追溯
```

### 求职工程验收

- 有模块依赖测试和 DDD 领域测试；
- 有 PostgreSQL/Redis/MinIO/RabbitMQ 的真实集成测试；
- 有 Outbox/Kafka（若进入 M11）的事件和故障证据；
- 有 k6 压测、JFR/jstack/GC 日志实验；
- 有微服务数据所有权、超时、熔断、降级和跨服务 Trace；
- 有 Kubernetes 探针、资源、发布、回滚和排障记录；
- 有 Istio 10/90 灰度、mTLS、授权和回滚记录。

每个核心写操作必须有权限、错误语义、测试和审计证据。没有证据的能力标记为“计划/未验证”。

## 11. 默认产品决策

- 首个试用对象：3～10 人摄影/短视频小团队；真实用户验证后可调整。
- P0～M6 使用 Fake Provider，M7 前决定是否接入真实 Provider。
- MVP 做文本评论和时间码评论，画面区域评论后置。
- 首期采用本地 Docker Compose，后续使用 kind/minikube 练习 Kubernetes。
- 文件先限制测试规格，不做 GPU/真实转码；交付链接短期有效。
- MVP 按 Team/Project 隔离，不做复杂企业多租户。
- 后端/API 优先，前端先用 API 工具或极简管理页。
