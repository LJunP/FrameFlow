# 下次继续 FrameFlow 开发启动指南

> 当前状态：P0-Prep、P0 和 M01 功能任务已完成；`FF-M01H-001=IN_PROGRESS`。本指南的当前入口是 M01-Hardening 与 M02 Contract Gate 草案收敛，不是重新执行 P0，也不是派发 M02 代码。

## 1. 当前唯一工作区

```text
FrameFlow 仓库根目录（启动时由调用方提供绝对路径，本文不写死个人路径）
```

禁止访问或引用 `jcm_media_api` 的代码、配置、数据库、消息、Webhook、测试环境、凭据或素材。仓库内文档禁止出现个人主目录绝对路径；路径一律使用仓库相对路径。

`.zcode/` 是用户明确保留且已被 `.gitignore` 排除的本地工具目录。可以留在工作区，但不读取其中旧计划来判断项目现状，不把它当作权威文档、任务输入或证据，也不将它加入正式交付包。

## 2. 当前默认决策

- JDK 17、Spring Boot 3.4.x、Maven（与 Java 17 匹配的版本组合）；
- PostgreSQL 是 MVP 唯一事务主库；
- MySQL 只在 M13 的 `identity-service` 使用，不做双写；
- P0～M4-A 不引入 Redis、MinIO、RabbitMQ、Kafka、Spring Cloud、Kubernetes、Istio；MinIO 自 M4-B 首次引入。M05 Redis 为可选 hardening/engineering-lab，不阻塞 MVP。
- P0 首期使用 Docker Compose；
- 前端放在本仓库 `frameflow-web/`，阶段为 M01-F/M04-F/M08-F；采用 React + TypeScript + Next.js App Router/BFF + Tailwind CSS + shadcn/ui + TanStack Query + openapi-typescript，每个 Task 派发时锁定当时受支持的精确版本；
- Fake Provider 优先，真实 Provider 后置；
- Team/Project 是 MVP 资源隔离边界。

## 3. P0-Prep 复核（已通过）

```text
[x] 只在本工作区工作
[x] 没有真实密钥、Token、客户数据、素材或旧项目配置
[x] README、三书、治理、API、测试和开发计划已阅读
[x] PostgreSQL 唯一主库和 Flyway 方案已确认
[x] Java/JDK 与 Maven 可验证；Docker daemon 留待 P0 启动前验证
[x] PP/P0/M01 任务 JSON 通过结构与引用完整性校验
[x] P0/M01 scope、non-goals、writeSet、命令白名单和验收标准已确认
[x] 编号、前置依赖、契约、Evidence ID 和索引一一对应
[x] .zcode/ 保留但被忽略、非权威且不进入正式交付包
[x] EV-FF-PP-001-01～05 已生成真实原始输出且全部通过
```

Stinky Cobbler 或其他研发控制面可以提供受限任务、只读核验、角色隔离和结构化证据，但不得成为无限期建设研发平台的理由；优先建立最小可审计流程。

`FF-PP-001`、`FF-P0-001`、`FF-M01-001` 已标记 `DONE`。当前只继续已授权的 `FF-M01H-001`，并把 M02 Contract Gate 保持为 DRAFT；其数据、API、权限、测试与 Evidence 决策未批准前，不得写 M02 业务代码或修改 `docs/04-api/openapi/frameflow-v1.yaml`。

## 4. 当前任务

进入条件：以 `FF-M01H-001` 的现有 Capability Grant 与 writeSet 为界，不扩大到 M02 实现。

当前产出：

- M01 证据脱敏、Grant/Receipt/预算真实性与 commit 锚点校验；
- Application Port/ArchUnit 边界、并发冲突、用户 ACTIVE 状态、JWT 轮换与 OpenAPI 功能差异验证；
- M02 Contract Gate 草案、M01-F/M04-F/M08-F 与 M02-001～004 的 DRAFT/NOT_READY Task Capsule。

不得实现 M02 业务、前端代码、Redis 或其他后续中间件。

## 5. M01H 完成后

必须有：

- `FF-M01H-001` 全部 Acceptance/Test/Evidence PASS 并有合法 Receipt；
- M02 Contract Gate 的 UNKNOWN 列表得到用户逐项批准；
- 权威 OpenAPI 的 M02 变更另行受控完成；
- 调度器再决定是否提升 `FF-M02-002`、`FF-M01F-001` 等任务状态。

## 6. 每次会话结束回写

只记录实际完成内容、命令与结果、测试证据、风险、待补充项和下一步。不得编造生产部署、用户反馈、线上故障、性能数字或 AI 成本。
