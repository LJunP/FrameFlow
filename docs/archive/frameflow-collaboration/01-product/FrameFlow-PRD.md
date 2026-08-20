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
以 PostgreSQL 为事实源的通用幂等；必要的缓存/限流可在 Redis hardening 实验中验证
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
复杂炫酷前端（当前只做管理后台级前端，不做视频剪辑器级交互前端）
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

> 轨道划分（DECISION）：`product-mainline` 包含后端 P0～M08 与前端 M01-F/M04-F/M08-F；M08 只是 backend gate，M08-F 才是产品 MVP gate，真实用户验证在 M08-F 之后开始。`engineering-lab` 包含可选 M05 Redis hardening 与 M09～M17，未选的实验不阻塞产品主线。

| 阶段 | 轨道 | 产品/工程目标 | 技术重点 | 不是当前阶段的内容 |
|---|---|---|---|---|
| P0～M01-H | product-mainline | 基座、身份功能与加固 | Java 17、Spring Boot、PostgreSQL、身份/安全门禁 | M02 业务实现 |
| M01-F | product-mainline | `frameflow-web/` 基座、认证与团队界面 | React + TypeScript + Next.js BFF + Tailwind + shadcn/ui + TanStack Query | 项目/素材/审核页面 |
| M02～M04-A | product-mainline | 项目/Brief、任务、本地素材核心业务 | PostgreSQL、MyBatis-Plus、Flyway、事务/版本 | MinIO、消息、微服务 |
| M04-F | product-mainline | 项目/Brief、任务/评论、素材界面 | 依赖 M02/M03/M04-A 后端 Gate | 审核/交付闭环 |
| M04-B、M06～M08 | product-mainline | MinIO、可靠异步、AI suggestion、审核/交付 backend gate | MinIO、RabbitMQ、Fake/Provider Adapter | Kafka、微服务、K8s |
| M08-F | product-mainline | 审核/交付界面与全栈 E2E，形成产品 MVP gate | 依赖 M07/M08，覆盖审核、通知、审计与交付 | 绕过门禁的用户试用 |
| M05 | engineering-lab（可选） | Redis 缓存/限流/幂等加速与降级实验 | PostgreSQL 始终是事实源 | 把 Redis 变成 MVP 前置 |
| M9～M12 | lab | 领域规则和可靠性成熟 | DDD、Outbox/Kafka、Testcontainers、压测、JVM | 过早拆分服务 |
| M13～M15 | lab | 服务独立部署和运行治理 | Gateway、Nacos（Compose）、Feign、Resilience4j、OTel、Prometheus/Grafana | 十几个微服务 |
| M16 | lab | 本地云原生平台实践 | Docker、Helm、kind/minikube、Kubernetes、Nginx Ingress | 生产集群宣称 |
| M16-G | lab | 受限服务网格实验 | Istio 灰度、mTLS、AuthorizationPolicy、故障注入 | 无业务目的的全量 Mesh |
| M17 | lab | 求职交付 | 证据索引、架构演示、故障复盘、面试材料 | 编造生产经历 |

### 7.1 前端阶段计划（DECISION）

前端是本源码单仓库的 `frameflow-web/`，不建立独立仓库。浏览器通过同源 Next BFF 访问后端 `/api/v1/*`。

| 阶段 | 轨道 | 前置 | 目标 | 关键内容 |
|---|---|---|---|---|
| M01-F | product-mainline | `FF-M01H-001=DONE` | 前端基座与认证界面 | BFF、登录/注册、团队管理、HttpOnly Cookie |
| M04-F | product-mainline | M01-F + M02/M03/M04-A DONE | 核心业务界面 | 项目/Brief 版本、任务/评论、素材版本上传与浏览 |
| M08-F | product-mainline | M04-F + M07/M08 DONE | 协作与交付界面，产品 MVP Gate | 审核流、时间码批注、基础通知、交付包、客户确认、全栈 E2E |

前端技术选型（ADR-012）：

```text
语言：TypeScript
框架：React
元框架：Next.js（App Router + BFF）
样式：Tailwind CSS
组件：shadcn/ui
HTTP：TanStack Query（缓存/重试/loading）
状态管理：Zustand（轻量）
类型生成：openapi-typescript（唯一选择，从后端 OpenAPI YAML 生成 TS 类型）
版本：Task 派发时选当时官方受支持的稳定版，并在 Task、package.json 与 lockfile 中精确锁定
```

前端不做：

```text
浏览器内视频剪辑器
实时协同编辑（如 Figma/在线文档级）
移动端 App
复杂炫酷动画/3D 渲染
```

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

M08 只证明后端闭环；只有 M08-F 的 `frameflow-web/` 全栈 E2E、安全 Cookie 边界、审计/基础通知和可用性验收全部 PASS，才能宣称产品 MVP Gate 通过并开始真实用户验证。

### 求职工程验收

- 有模块依赖测试和 DDD 领域测试；
- 有 PostgreSQL/MinIO/RabbitMQ 的真实集成测试；若选择 M05 Redis hardening，再追加 Redis 降级与一致性证据；
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
- 前端只放在本仓库 `frameflow-web/`，按 M01-F/M04-F/M08-F 推进；不做视频编辑器级前端。
- Next BFF 保有 Refresh Token 的 HttpOnly Cookie 边界；Refresh Token 禁止 localStorage/sessionStorage/IndexedDB。后端为每次请求生成 `X-Request-Id`，跨请求业务关联单独使用 `X-Correlation-Id`。
- `governance` 模块是 MVP `audit_logs` 和基础站内 `notifications` 的唯一表所有者；业务模块只能通过 Port/应用事件写入。
