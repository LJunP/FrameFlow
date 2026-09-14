# AGENTS.md — FrameFlow Select

FrameFlow Select 是面向 AI 生成短视频的批量质检与优选平台。本仓库按**产品工程**推进，不是学习课程。

## 权威来源（优先级递减）

1. 当前对话中的明确指令
2. `docs/01-产品与领域设计.md`（产品与领域）
3. `docs/02-技术架构与技术栈.md`（技术选型）
4. `docs/03-开发路线.md`（功能切片与任务）
5. `docs/api/frameflow-v1.yaml`（API 契约，与运行时双向锁定）

## 技术基线（勿擅改）

- **JDK 17**：根 pom 的 maven-enforcer 硬锁 `[17,18)`，非 17 构建直接失败
- 其余选型以 docs/02 为准：Spring Boot 3.4.5、PostgreSQL 16、Redis、RabbitMQ、MinIO、Python 3.11、Next.js。变更须先说明理由

## 红线

- `main` 是唯一长期主线；隔离开发用临时 `codex/<短名>` 分支，合回后清理
- 允许向 origin 推送 `main` 与 tags；不 force push、不改写已推送历史、不删除 `.git`
- 不读取工作区外 Secret；不提交真实客户媒体、密钥、密码
- 不修改已执行的 Flyway 迁移；不通过改阈值/测试/历史制造 PASS
- `ANALYSIS_ERROR` 不得伪装成视频不合格
- 语义结论不得自动淘汰候选；只有带证据的确定性规则可触发 `AUTO_REJECT`
- 机器决定与人工决定分离：人工调整只叠加标记，不改写机器结果
- 所有资源查询必须校验调用者的团队归属
- 购买服务器、SSH、DNS、生产发布由有权限的人亲手执行；仓库内准备配置与脚本
- 生产可用与试点价值只能依据真实运行数据判断

## 开发约定

- 核心逻辑注释写「为什么」和「改坏会怎样」，不刷样板注释
- 改 API 必须同步 `docs/api/frameflow-v1.yaml`，必要时同步 README
- 验证：`./mvnw -B verify`、Worker pytest、`npm --prefix frameflow-web run test:contracts` / `typecheck` / `build`
- conventional commits：feat / fix / test / docs / chore
