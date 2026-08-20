# Prompt 01：模块化单体与 MVP

在 P0～M8 范围内实现 FrameFlow 模块化单体，不提前引入微服务、Kafka、Kubernetes 或 Istio。

## 目标顺序

```text
P0 基座
→ Identity/Security/JWT/RBAC
→ Project/Brief/事务
→ Workflow/任务/乐观锁
→ Asset/本地存储与 MinIO
→ Redis 缓存/幂等/限流
→ RabbitMQ 任务/重试/DLQ
→ Fake/Provider Adapter/Suggestion
→ Review/Delivery/审计
```

## 必须遵守

- PostgreSQL 是 MVP 唯一事务主库；Flyway 管理迁移。
- MyBatis-Plus + XML；领域层不依赖 MyBatis/Redis/MQ/MinIO SDK。
- 模块不得直接访问其他模块 Repository、Mapper、Entity 或表。
- AI 输出只能进入 Suggestion，人工确认后才进入正式业务数据。
- 素材版本不可变、交付引用固定版本、确认幂等。
- 每个模块同时交付代码、迁移、单测/API/集成测试、README 演示和证据索引。

## 验收

必须有完整 E2E：登录 → 建项目 → Brief → 任务 → 上传 → 异步分析 → suggestion → 审核/返工 → 客户确认 → 锁定交付 → 审计。
