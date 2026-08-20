# FrameFlow Select

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
