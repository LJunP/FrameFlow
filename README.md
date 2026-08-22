# FrameFlow Select

> 🔒 **本分支已冻结（2026-08-22）——只读答案册，禁止复制实现**
> 这是 Agent 自动生成的 FrameFlow Select 完整 MVP 参考实现，仅供项目所有者学习对照，不作为开发基线，不再更新；项目红线禁止复制本分支实现充当个人成果。
> 最终验证通过的冻结状态锁定在 tag `frameflow-select-agent-mvp-v1.0.0`（commit `c893550`）。
> **最新主线分支：[`frameflow-select/learning-main`](../../tree/frameflow-select/learning-main)**（学习优先重建：全部业务代码由项目所有者亲手从零实现）。

FrameFlow Select 是面向 AI 生成短视频团队的批量质检、问题定位、重复聚类与 Top-K 优选平台。

```text
Prompt / Brief / Quality Profile
→ Batch of Candidate Videos
→ Technical QA
→ Evidence-grounded AI QA
→ Duplicate Clustering
→ Ranking and Top-K
→ Human Review
→ Export
```

## 当前状态

项目正在按照 `.frameflow/master-plan.yaml` 自治开发。真实状态见：

```text
.frameflow/state.json
docs/00-governance/project-status.md
```

## 权威文档

长期权威文档仅有六本，位于 `docs/core/`。

## 架构

```text
Java Spring Boot modular monolith
Python AI worker
Next.js Web
PostgreSQL
MinIO
RabbitMQ
FFmpeg
```

## 非目标

本项目不做视频生成、浏览器剪辑、自动发布、爆款预测、通用内容审核、通用 Agent Infra、微服务、Kafka、Kubernetes 或 Istio。

## 本地运行

完成相应阶段后使用：

```bash
./scripts/dev/start-local.sh
./scripts/test/all.sh
```

若脚本尚未生成，以 `.frameflow/state.json` 为准，不要使用旧 FrameFlow 协作产品启动指南。

## 历史

旧视频协作平台文档保存在：

```text
docs/archive/frameflow-collaboration/
```

它们只用于历史追踪，不是当前事实源。
