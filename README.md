# FrameFlow Select

**面向 AI 生成短视频的批量质检与优选平台。**

一次生成几十到几百条候选视频后，FrameFlow Select 帮你完成批量质检（确定性
检测 + 语义检测）、问题定位（每条问题带规则、时间码、证据）、重复聚类、
批次排名与 Top-K 优选，把最值得人工观看的候选筛出来——同时尽量不错杀
真正优秀的视频。

这也是一个**学习驱动**的长期工程：项目所有者按 `docs/03` 的功能切片
（F1–F11）亲手实现全部核心代码、部署与运维，在做产品的过程中掌握
后端为主的完整技术栈。

## 文档（唯一权威）

- [docs/01-产品与领域设计.md](docs/01-产品与领域设计.md) — 定位、领域实体、状态机、质检模型
- [docs/02-技术架构与技术栈.md](docs/02-技术架构与技术栈.md) — 模块化单体架构、选型决策、环境与部署
- [docs/03-开发与学习路线.md](docs/03-开发与学习路线.md) — 功能切片 F1–F11、任务分解、完成标准
- [.learning/PROGRESS.md](.learning/PROGRESS.md) — 当前进度（唯一状态文件）

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 17 + Spring Boot 3.x + Maven 多模块（模块化单体） |
| 数据 | PostgreSQL 16（唯一事实源）+ Flyway + MyBatis |
| 中间件 | Redis（缓存/限流）· RabbitMQ（异步任务）· MinIO（对象存储） |
| AI Worker | Python 3.11 + FFmpeg/ffprobe + OpenCV + 多模态模型 Adapter |
| 前端 | Next.js + React + TypeScript（刻意轻量） |
| 部署 | Linux VPS + Docker Compose + Nginx/HTTPS + GitHub Actions |

## 仓库结构

```text
frameflow-app/        Java 后端（identity + product 模块）
frameflow-ai-worker/  Python 质检 worker
frameflow-web/        Next.js 轻前端
docs/                 三份核心文档（产品 / 架构 / 路线）
.learning/            PROGRESS.md 进度追踪
infra/                local/dev/staging/production 环境说明与模板
```

## 本地验证

```bash
# Java
./mvnw -B -q verify
# Python
cd frameflow-ai-worker && python3 -m pytest tests -q
# Web
cd frameflow-web && npm install && npm run build
```

## 参考

分支 `archive/frameflow-select-agent-mvp-v1`（tag `frameflow-select-agent-mvp-v1.0.0`）
保存了一份由 Agent 自动生成的完整参考实现，**仅供学习对照，禁止复制充当
自己的实现**。当前主线代码全部由项目所有者亲手编写。
