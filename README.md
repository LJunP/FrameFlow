# FrameFlow Select(学习优先重建)

> 状态:LEARNING_REBUILD_READY(学习主线,业务功能尚未由项目所有者亲手实现)

FrameFlow Select 面向 AI 生成短视频团队的批量质检、问题定位、重复聚类、质量评估、
批次排名与 Top-K 优选平台。本仓库现为**学习优先**长期工程:项目所有者将按
LR0-LR17 路线亲手实现全部核心代码、服务器部署、中间件、上线、监控、备份与故障处理。

## 仓库结构

- frameflow-app —— Java 21 + Spring Boot 3.x 最小骨架(健康检查,无业务)
- frameflow-ai-worker —— Python 3.11 最小包(无业务检测器)
- frameflow-web —— Next.js + TypeScript 最小页面(无业务页面)
- docs/books/ —— 六本唯一权威手册(BOOK-01 至 BOOK-06)
- .learning/ —— 机器可读学习控制(roadmap / state / skill-matrix / tasks)
- infra/ —— 四套环境(local/dev/staging/production)目录与模板,配置由项目所有者亲手完成
- docs/archive/ —— 归档区:Agent 自动生成的 MVP 收档于此(含旧自治包、旧 evidence)

## 参考基线(只读对照,不可复制负责)

自动生成的完整 MVP 已冻结,仅用于学习对照:

- Tag:frameflow-select-agent-mvp-v1.0.0(commit c893550)
- Branch:archive/frameflow-select-agent-mvp-v1

## 学习起点

1. 阅读 docs/books/BOOK-01(目标与宪章)、BOOK-06(路线与验收)
2. 完成第一个 HUMAN_CORE 任务:.learning/tasks/LR1-H001.yaml
3. 每个阶段推进见 .learning/roadmap.yaml 与状态 .learning/state.yaml

## 本地最小骨架验证

```bash
# Java
./mvnw -B -q verify
# Python
cd frameflow-ai-worker && python3 -m pytest tests -q
# Web
cd frameflow-web && npm install && npm run build
```
