# 下次继续 FrameFlow 开发启动指南

> 当前状态：P0-Prep 已于 2026-08-13 通过五类机器门禁（37/37 PASS）。P0 工程基座已实现，正在完成证据与状态收口；正式业务代码（M01）尚未开始。

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
- P0～M4-A 不引入 Redis、MinIO、RabbitMQ、Kafka、Spring Cloud、Kubernetes、Istio 或复杂前端；MinIO 自 M4-B 首次引入。
- P0 首期使用 Docker Compose；
- 后端/API 优先，先用 API 工具或极简管理页；
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

`FF-PP-001` 已标记 `DONE`。用户授权、Git 基线和 P0 派发已完成；当前只收口 P0 证据，不重跑或改写 PP 原始证据。

## 4. P0 任务

完成状态：P0-Prep 已判定 PASSED、用户已分别授权 Git 初始化与执行 P0、必要环境预检通过、`FF-P0-001` 已取得受限 Capability Grant，Git 基线和独立 P0 分支已建立。当前只剩六条证据、Receipt、卫生提交和状态回写。

实现：

- Maven + Spring Boot 3.x 基座；
- `/health`：进程存活，不依赖数据库；
- `/readiness`：必要依赖可用才就绪；
- PostgreSQL Docker Compose；
- Flyway、`.env.example`、本地配置样例；
- JUnit/MockMvc 健康检查测试；
- README 启动、停止、测试、清理命令。

不得提前实现 M01 业务或引入后续中间件。

## 5. P0 完成后

必须有：

- `mvn clean verify` 的真实输出；
- Compose 配置和 PostgreSQL 健康检查结果；
- health/readiness 在依赖可用与不可用时的测试；
- README、project-status、evidence-index 更新；
- 下一模块 M01 的进入条件。

完成 P0 后停下，除非用户另行授权，不派发或实现 M01。

## 6. 每次会话结束回写

只记录实际完成内容、命令与结果、测试证据、风险、待补充项和下一步。不得编造生产部署、用户反馈、线上故障、性能数字或 AI 成本。
