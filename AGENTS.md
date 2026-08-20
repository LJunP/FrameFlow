# AGENTS.md — FrameFlow Select 自治开发约定

## 1. 当前产品

FrameFlow Select 是面向 AI 生成短视频团队的批量质检、问题定位、重复聚类与 Top-K 优选平台。

核心闭环：

```text
Project / Prompt / Brief / Quality Profile
→ Generation Batch
→ Candidate Videos
→ Deterministic QA
→ Evidence-grounded AI QA
→ Duplicate Clustering
→ Ranking / Top-K
→ Human Review
→ Exported Selection Set
```

它不是视频生成器、浏览器剪辑器、自动发布系统、爆款预测系统、通用内容审核平台，也不是 Agent Infra。SourceLens-AIOS 与本项目保持独立。

## 2. 唯一启动入口

开始任何工作前必须读取：

```text
FRAMEFLOW_SELECT_AUTOPILOT/START-HERE.md
FRAMEFLOW_SELECT_AUTOPILOT/execution/AUTONOMOUS-EXECUTION-CONTRACT.md
FRAMEFLOW_SELECT_AUTOPILOT/execution/MASTER-PLAN.yaml
```

如果 `.frameflow/state.json` 已存在，则它是当前执行进度事实源；否则从包内初始状态开始。

## 3. 权威优先级

产品与质量事实：

```text
用户当前总提示词
> AUTONOMOUS-EXECUTION-CONTRACT.md
> BOOK-01
> BOOK-02 / BOOK-03
> BOOK-04
> 机器契约与 Schema
> BOOK-05（概念阶段与外部验证）
> BOOK-06
> ADR
> 代码注释、旧文档和历史 Task
```

自治执行顺序：

```text
用户当前总提示词
> AUTONOMOUS-EXECUTION-CONTRACT.md
> .frameflow/master-plan.yaml（安装前为包内 MASTER-PLAN.yaml）
> .frameflow/state.json
> 当前 Task Capsule
```

`MASTER-PLAN.yaml` 是 BOOK-05 针对单会话本地 MVP 的批准后细分；它可以把一个概念阶段拆成多个工程 Stage，但不能改变 BOOK-01～BOOK-04 的产品边界、质量含义或自动决策阈值。

旧 FrameFlow 视频协作方向的 PRD、M02～M17 路线、微服务/Kafka/Kubernetes/Istio 计划和通用 Agent 控制文档全部是 Legacy，不得指导新开发。

## 4. 单会话自治授权

用户已预先授权在当前 Git 工作区内完成以下本地、可回退操作：

- 读取、创建、修改和移动工作区文件；
- 创建本地分支、Tag 和 Commit；
- 运行 Git、Maven、Java、Python、Node、FFmpeg、Docker 和测试命令；
- 从官方依赖源下载构建依赖；
- 创建本地数据库、对象存储、消息队列和测试数据；
- 创建临时 worktree 进行独立验证；
- 按 Master Plan 连续推进到本地 MVP 完成；
- 对满足自动 Gate 的本地任务和阶段执行“Owner Proxy Acceptance”。

本授权不包括：push、force-push、修改远端、云部署、生产发布、付费资源开通、读取工作区外 Secret、使用真实客户媒体、删除 `.git`、重写共享历史或执行不可逆外部操作。

## 5. 不向用户追问的默认规则

普通实现选择不得暂停询问。信息不足时：

1. 选择最小、可回退、与现有代码兼容的方案；
2. 记录假设和 ADR（仅在长期且难回退时）；
3. 缺少 API Key 时使用 Fake/Local Provider，继续完成可测试产品；
4. 缺少真实视频时生成本地合成测试集，继续完成本地 MVP；
5. 缺少某项可选工具时采用包内 Fallback；
6. 外部市场验证保持 `UNVERIFIED`，不得阻塞本地工程完成；
7. 只有不可逆外部动作、真实 Secret 或法律授权不可替代时才允许停止。

## 6. 执行纪律

- 同一时间只有一个 Active Stage、一个主 Task；
- 每个 Task 先生成 `.frameflow/tasks/<TASK-ID>.json`；
- 每个 Task 必须经历 Baseline、Implement、Self-test、Independent Verify、Accept/Reject；
- Verifier 优先使用独立子代理和临时 Git worktree；运行时不支持时，用新 worktree、重新读取任务和清空实现上下文的方式替代；
- 同一方案最多两次尝试；第二次失败后必须缩小范围或采用预定义 Fallback，禁止无限重试；
- 不得通过修改测试、阈值、Gold Set、历史 Flyway 或权威契约来制造 PASS；
- 任何分析失败必须记录为 `ANALYSIS_ERROR`，不能伪装成视频不合格；
- 不创建第七本长期总纲；新长期事实必须修改现有六本书或机器契约。

## 7. 技术边界

默认目标架构：

```text
frameflow-app / Java modular monolith
frameflow-web / Next.js + TypeScript
frameflow-ai-worker / Python
PostgreSQL
MinIO/S3-compatible storage
RabbitMQ
FFmpeg/ffprobe
```

禁止在 `LOCAL_MVP_COMPLETE` 前引入：微服务拆分、Kafka、Kubernetes、Istio、通用 Agent 编排、视频生成、自动发布、复杂计费和移动端。

## 8. Git 与安全红线

永远禁止：

```text
git reset --hard
git clean -fd / -fdx
删除 .git
修改已执行 Flyway migration
提交私钥、Token、API Key、真实密码或真实客户媒体
自动 push / merge 到远端 / 发布
读取或修改工作区外 SourceLens-AIOS
```

迁移前必须保存 Commit、分支、状态、二进制 Diff 和已知异常。所有破坏性清理必须先复制到 `evidence/pivot/legacy-snapshot/`。

## 9. 完成标准

本会话的自主交付目标是：

```text
LOCAL_MVP_COMPLETE
```

其精确定义在：

```text
FRAMEFLOW_SELECT_AUTOPILOT/execution/COMPLETION-CRITERIA.yaml
```

真实客户试点、真实 Provider 指标和市场价值只能标记为：

```text
EXTERNAL_VALIDATION_PENDING
```

不得把本地合成数据结果包装成真实市场验证。

## 10. 每次恢复

如果会话、进程或上下文发生中断：

1. 重新读取本文件；
2. 读取 `.frameflow/state.json`；
3. 校验当前 HEAD 与最后一次 Gate Commit；
4. 从第一个未接受 Task 恢复；
5. 不重复已通过 Gate，不重新规划整个项目。
