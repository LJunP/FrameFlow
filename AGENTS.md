# AGENTS.md — FrameFlow Select 协作规则

## 1. 唯一目标

Agent 实现整个产品，所有者通过阅读带注释的源码掌握全部开发技术（第一）；
产品价值真实可用（第二）；求职展示（第三）。三者顺序不可颠倒。

## 2. 权威来源（优先级递减）

1. 项目所有者当前提示词
2. docs/01（产品与领域）、docs/02（技术架构）、docs/03（开发与学习路线）
3. .learning/PROGRESS.md（进度状态）
4. 后续沉淀的 ADR 与 Runbook

## 3. 协作分工

- **Agent（开发方）**：实现每个功能（F1–F11）的全部代码、测试与部署配置。
  强制遵守 docs/03 §0.2 注释规范——核心逻辑用 `// ★ 核心：` 标注
  （做什么、为什么、改坏会怎样），调用链入口标注阅读顺序，不刷样板注释；
  每个功能交付《源码导读》（docs/guides/F<n>-源码导读.md）。
- **所有者（学习方 + 决策方）**：阅读源码与导读、本地运行验证、提问；
  在 PROGRESS.md 维护学习状态（未读/阅读中/已理解）；功能验收、方向变更、
  发布审批只由所有者拍板。
- **物理动作例外**：服务器购买、SSH 操作、DNS 配置、生产发布只能所有者
  亲手执行；Agent 准备全部配置、脚本与逐步指引。

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
- 不得代标所有者的学习状态（"已理解"只能由所有者本人标记）
- 不得自行设置 PROJECT_COMPLETE / PILOT_VALUE_PROVEN（只能由所有者依据数据批准）
- ANALYSIS_ERROR 不得伪装成视频不合格
- 答案册分支只读对照（冻结状态以 tag `frameflow-select-agent-mvp-v1.0.0` 为准）：
  可作架构参考，须按当前文档与 JDK 17 基线重新实现，不得直接搬移旧代码

## 6. 当前状态速览

- 主线分支：`frameflow-select/learning-main`（唯一活跃分支）
- 进度：`.learning/PROGRESS.md`（当前 F1 工程基线与用户认证）
- 流程、注释规范与交付标准：`docs/03-开发与学习路线.md` §0
