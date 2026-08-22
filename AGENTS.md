# AGENTS.md — FrameFlow Select 协作规则

## 1. 唯一目标

项目所有者本人通过亲手开发这个产品，从零建立真实软件工程能力（第一）；
产品价值真实可用（第二）；求职展示（第三）。三者顺序不可颠倒。

## 2. 权威来源（优先级递减）

1. 项目所有者当前提示词
2. docs/01（产品与领域）、docs/02（技术架构）、docs/03（开发与学习路线）
3. .learning/PROGRESS.md（进度状态）
4. 后续沉淀的 ADR 与 Runbook

## 3. 任务所有权

- HUMAN_CORE：所有者亲手完成核心代码/配置/部署。Agent 不实现、不给完整答案，
  只给任务说明、分层提示、审查命令、故障分析、Code Review。
- PAIR：所有者先完成核心，Agent 可协助异常处理、测试补齐、重构、SQL 调优、
  前端打磨、CI/CD 配置。
- AGENT_SUPPORT：Agent 可自主完成仓库整理、归档、测试基础设施、检查脚本、
  代码审查、独立验证。

## 4. 技术基线（勿擅改）

- **JDK 17**：根 pom 的 maven-enforcer 已硬锁 `[17,18)`，非 17 构建直接失败；
  不得升级版本、不得移除该约束
- 其余选型（Spring Boot 3.4.5、PostgreSQL 16、Redis、RabbitMQ、MinIO、
  Python 3.11、Next.js）以 docs/02 为准；任何变更须先获所有者批准并记录理由

## 5. 硬性红线

- 不 push 远端、不改写历史、不删除 .git
- 不向已冻结分支提交（main、archive/frameflow-select-agent-mvp-v1）
- 不读取工作区外 Secret；不提交真实客户媒体、密钥、密码
- 不修改已执行迁移；不通过改阈值/测试/历史制造 PASS
- HUMAN_CORE 任务 Agent 不得代做，不得宣布所有者已掌握某项能力
- 不得自行设置 PROJECT_COMPLETE / PILOT_VALUE_PROVEN（只能由所有者依据数据批准）
- ANALYSIS_ERROR 不得伪装成视频不合格
- 答案册只读对照，冻结状态以 tag `frameflow-select-agent-mvp-v1.0.0` 为准；
  禁止复制其实现充当所有者成果

## 6. 当前状态速览

- 主线分支：`frameflow-select/learning-main`（唯一活跃分支）
- 进度：`.learning/PROGRESS.md`（当前 F1 工程基线与用户认证）
- 流程、任务分解与验收标准：`docs/03-开发与学习路线.md`（§0 含 Git 分支与
  commit 规范）
