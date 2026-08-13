# FrameFlow 开发计划（P0-Prep + P0～M17）

> 状态：**开发进度为 0**。本文件定义任务顺序、轨道和门禁；任务包格式与编号见 `task-capsule-spec.md` 和 `schemas/task-capsule.schema.json`。
> 仓库模式：A（源码单仓库）——docs/ 与代码同库，本仓库承载全部业务代码。

## 1. 执行顺序

```text
P0-Prep（文档收敛门禁）
→ P0 → M01 → M02 → M03 → M04-A → M04-B → M05 → M06 → M07 → M08（产品主线，不可跳过）
→ M9 → M10 → M11 → M12-A/B/C/D → M13 → M14 → M15
→ M16-A/B/C/D/E/F → M16-G → M17（工程实验，按目标选择执行）
```

## 2. 轨道划分

| 轨道 | 范围 | 目的 | 跳步规则 |
|---|---|---|---|
| product-mainline | P0～M8 + P0-Prep | 可演示的产品 MVP | 不可跳过；M8 后做真实用户验证 |
| engineering-lab | M9～M17 | Java 深度、微服务、K8s、Istio、求职证据 | 按学习和演示目标选择；依赖的前置任务必须先完成 |

`identity-service` 使用 MySQL 属于 engineering-lab 的 M13 决策，是工程实验目标，不是产品 MVP 架构理由；MVP 唯一事务主库始终是 PostgreSQL（ADR-002）。

## 3. 阶段任务包与门禁

| 阶段 | Task ID | 轨道 | 目标 | 关键验收 | 禁止提前引入 |
|---|---|---|---|---|---|
| P0-Prep | FF-PP-001 | prep | 执行基线收敛（仓库角色/归属/编号/任务协议/证据） | 当前态五类门禁全部 PASS，bootstrap 边界透明披露 | 任何业务代码、Git 初始化 |
| P0 | FF-P0-001 | mainline | 工程基座 | mvn verify、health/readiness、Compose、Flyway | Redis、MQ、Cloud、K8s |
| M01 | FF-M01-001 | mainline | 身份与授权 | 401/403/404、RS256/Refresh family、PostgreSQL 请求幂等、OWNER 不变量、requestId | Redis、MQ、微服务 |
| M2 | FF-M02-001 | mainline | 项目与 Brief | 单一 current、事务回滚、状态机 | 素材、MQ、AI |
| M3 | FF-M03-001 | mainline | 任务与乐观锁 | 旧版本 409 | Redis、MQ |
| M4-A | FF-M04A-001 | mainline | 素材与本地存储 | 上传校验、DB/文件补偿 | MinIO |
| M4-B | FF-M04B-001 | mainline | MinIO 适配 | 预签名、权限、补偿 | 上层 API 不变 |
| M5 | FF-M05-001 | mainline | Redis | 缓存/幂等/429/降级 | 分布式锁滥用 |
| M6 | FF-M06-001 | mainline | RabbitMQ | Confirm/ACK/DLQ/重放 | Kafka |
| M7 | FF-M07-001 | mainline | AI Suggestion | suggestion 不自动生效 | 真实 Key |
| M8 | FF-M08-001 | mainline | 审核与交付 | 完整 E2E、锁定、幂等确认 | 微服务 |
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
进入条件：上一任务包证据索引通过 + P0-Prep 门禁通过
完成定义：代码 + 测试 + 迁移 + 真实命令与原始输出 + 文档 + 证据回写
禁止事项：跳步、提前引入技术、无证据宣称完成、编号失配
回滚方式：每个任务包 JSON 中声明 rollback
```

## 5. 证据回写

每个任务包完成后按 evidenceId 更新 `docs/09-delivery/evidence-index.md` 与 `docs/00-governance/project-status.md`；`EV-*` 编号必须与任务包 JSON 中声明一致。
