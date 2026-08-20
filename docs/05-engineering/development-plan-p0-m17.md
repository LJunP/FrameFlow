# FrameFlow 开发计划（P0-Prep + P0～M17）

> 当前事实：P0、M01 功能与 M01-Hardening 已完成，`FF-M01H-001=DONE`。现阶段是 **M02 Contract Gate 草案**；M02 实现尚未开始，M01-F 仍需锁定精确版本与派发授权。
> 仓库模式：A（源码单仓库）——docs/ 与代码同库，本仓库承载全部业务代码。

## 1. 执行顺序

```text
P0-Prep → P0 → M01 → M01-H
  ├→ M01-F
  └→ M02 Contract Gate → M02 实现 → M03 → M04-A → M04-F
       → M04-B → M06 → M07 → M08（backend gate）→ M08-F（product MVP gate）→ 真实用户验证

可选旁路：M05 Redis hardening（engineering-lab，不阻塞 M06/M08/M08-F）
工程实验：M9 → M10 → M11 → M12-A/B/C/D → M13 → M14 → M15
→ M16-A/B/C/D/E/F → M16-G → M17（工程实验，按目标选择执行）
```

前端三阶段均属 `product-mainline`，代码只放在本仓库 `frameflow-web/`。M01-F 直接依赖 M01H DONE；M04-F 依赖上一前端阶段及 M02/M03/M04-A DONE；M08-F 依赖上一前端阶段及 M07/M08 DONE。

## 2. 轨道划分

| 轨道 | 范围 | 目的 | 跳步规则 |
|---|---|---|---|
| product-mainline | P0-Prep、P0～M08（不含可选 M05）、M01-F/M04-F/M08-F | 可用的产品 MVP | M08 只是 backend gate；M08-F 通过后才允许真实用户验证 |
| engineering-lab | 可选 M05 + M9～M17 | Redis hardening、Java 深度、微服务、K8s、Istio、求职证据 | 按学习/演示目标选择；未选阶段不阻塞主线 |

`identity-service` 使用 MySQL 属于 engineering-lab 的 M13 决策，是工程实验目标，不是产品 MVP 架构理由；MVP 唯一事务主库始终是 PostgreSQL（ADR-002）。

## 3. 阶段任务包与门禁

| 阶段 | Task ID | 轨道 | 目标 | 关键验收 | 禁止提前引入 |
|---|---|---|---|---|---|
| P0-Prep | FF-PP-001 | prep | 执行基线收敛（仓库角色/归属/编号/任务协议/证据） | 当前态五类门禁全部 PASS，bootstrap 边界透明披露 | 任何业务代码、Git 初始化 |
| P0 | FF-P0-001 | mainline | 工程基座 | mvn verify、health/readiness、Compose、Flyway | Redis、MQ、Cloud、K8s |
| M01 | FF-M01-001 | mainline | 身份与授权 | 401/403/404、RS256/Refresh family、PostgreSQL 请求幂等、OWNER 不变量、requestId | Redis、MQ、微服务 |
| M01-H | FF-M01H-001 | product-mainline | M01 安全/证据/架构加固 | Evidence 脱敏、ArchUnit、并发/用户状态、JWT 轮换、OpenAPI 差异 | M02 实现、前端代码 |
| M01-F | FF-M01F-001 | product-mainline | 前端基座与认证/团队 | `frameflow-web/`、Next BFF、HttpOnly Cookie、类型/构建/E2E | Refresh Token localStorage、M02 页面 |
| M02 Contract Gate | FF-M02-001 | product-mainline | 数据/API/权限/测试/Evidence 决策 | UNKNOWN 逐项批准；不写代码、不改 docs/04 | 将草案当成定稿 |
| M02 实现 | FF-M02-002～004 | product-mainline | 项目/成员/客户、Brief 版本、API/E2E | 单一 current、事务回滚、状态机、审计/基础通知 | Contract Gate 未批准就实现 |
| M3 | FF-M03-001 | mainline | 任务与乐观锁 | 旧版本 409 | Redis、MQ |
| M4-A | FF-M04A-001 | mainline | 素材与本地存储 | 上传校验、DB/文件补偿 | MinIO |
| M04-F | FF-M04F-001 | product-mainline | 项目/Brief、任务/评论、素材前端 | 依赖 M02/M03/M04-A DONE，端到端权限/版本语义 | 视频编辑器 |
| M4-B | FF-M04B-001 | mainline | MinIO 适配 | 预签名、权限、补偿 | 上层 API 不变 |
| M05 | FF-M05-001 | engineering-lab（可选） | Redis hardening | 缓存/幂等加速/429/降级；PostgreSQL 结果不变 | 分布式锁滥用、阻塞 MVP |
| M6 | FF-M06-001 | mainline | RabbitMQ | Confirm/ACK/DLQ/重放 | Kafka |
| M7 | FF-M07-001 | mainline | AI Suggestion | suggestion 不自动生效 | 真实 Key |
| M8 | FF-M08-001 | product-mainline | 审核与交付 backend gate | 后端 E2E、锁定、幂等确认、审计/基础通知 | 宣称产品 MVP 已通过 |
| M08-F | FF-M08F-001 | product-mainline | 审核/通知/交付前端与 product MVP gate | 依赖 M07/M08 DONE；全栈 E2E、可用性与安全门禁 | 通过前做真实用户验证 |
| M9 | FF-M09-001 | lab | DDD 深化 | 纯领域测试 | 大范围 API 变更 |
| M10 | FF-M10-001 | lab | PostgreSQL 深化 | EXPLAIN 前后对比 | MySQL 双写 |
| M11 | FF-M11-001 | lab | Kafka/Outbox | 事务内 Outbox、补发、DLT | - |
| M12-A/B/C/D | FF-M12A~D-001 | lab | 测试/压测/JVM | 原始报告、实验复盘 | 生产结论 |
| M13 | FF-M13-001 | lab | 微服务拆分 | 数据所有权、迁移/回滚、无跨库访问 | 共享数据库 |
| M14 | FF-M14-001 | lab | 服务治理 | 超时/熔断/降级实验 | 无限重试 |
| M15 | FF-M15-001 | lab | 可观测性 | 跨服务 Trace、ELK 检索 | 无证据宣称 |
| M16-A~F | FF-M16-001 | lab | Docker/Helm/K8s | kubectl 输出、回滚、HPA | 生产容量宣称 |
| M16-G | FF-M16G-001 | lab | Istio | 灰度/mTLS/策略/回滚 | 只提交 YAML |
| M17 | FF-M17-001 | lab | 求职交付 | evidence-index 完整 | 编造生产经历 |

## 4. 阶段门禁规则

```text
进入条件：任务包所有直接前置均 DONE，契约/授权/环境复核通过
完成定义：代码 + 测试 + 迁移 + 真实命令与原始输出 + 文档 + 证据回写
禁止事项：跳步、提前引入技术、无证据宣称完成、编号失配
回滚方式：每个任务包 JSON 中声明 rollback
```

## 5. 前端阶段说明

前端位于本仓库 `frameflow-web/`，浏览器只访问同源 Next BFF，BFF 访问后端 `/api/v1/*`。前端技术选型见 ADR-012。

| 阶段 | 前置 | 目标 | 关键内容 |
|---|---|---|---|
| M01-F | M01H DONE | 前端基座与认证界面 | App Router/BFF、Tailwind + shadcn/ui、登录/注册、团队管理、HttpOnly Cookie |
| M04-F | M01-F + M02/M03/M04-A DONE | 核心业务界面 | 项目/Brief 版本、任务/评论、素材版本上传与浏览 |
| M08-F | M04-F + M07/M08 DONE | 协作与交付界面 | 审核、时间码批注、基础通知、交付包、客户确认、product MVP gate |

前端技术选型（ADR-012）：TypeScript、React、Next.js（App Router/BFF）、Tailwind CSS、shadcn/ui、TanStack Query、Zustand、openapi-typescript。每个 Task 派发时选当时受支持的稳定版并精确锁定。Refresh Token 仅存 HttpOnly Cookie，禁止 localStorage。

前端不做：浏览器内视频剪辑器、实时协同编辑、移动端 App、复杂炫酷动画/3D 渲染。

## 6. 证据回写

每个任务包完成后按 evidenceId 更新 `docs/09-delivery/evidence-index.md` 与 `docs/00-governance/project-status.md`；`EV-*` 编号必须与任务包 JSON 中声明一致。
