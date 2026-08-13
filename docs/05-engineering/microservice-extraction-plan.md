# 微服务拆分计划

## 1. 前置门禁

在 M13 前必须通过：

- ArchUnit 模块边界测试；
- **workflow 模块拆分前置步骤**（M9 前完成）：先把任务/评论相关代码迁入 project 模块边界、审核/批注相关代码迁入 asset/delivery 边界，消除"workflow 跨两个服务共享模块"的含混状态（见 ADR-008 与 module-boundaries-and-dependency-rules.md）；
- MVP 完整 E2E；
- 每个候选模块的数据表清单和所有权（含治理表：audit_logs、outbox_events、idempotency_records、upload_sessions、ai_call_logs、prompt_templates）；
- API/事件契约；
- 单体基线压测和关键故障演练；
- 独立部署、扩缩容或故障隔离理由；
- 数据迁移（选定增量追平方案：snapshot + delta/watermark/cutover/rollback，见 M13 任务包）、兼容和回滚方案。

## 2. 提取顺序

### 第一步：Identity

- 将 Identity 模块提取为 `identity-service`；
- MySQL 独立迁移和测试；
- 先实现只读成员查询，再迁移登录和写入；
- 保留单体 Feature Flag 回退；
- 验证跨服务 Token、角色查询和超时。

### 第二步：Project

- 将项目、Brief、任务提取为 `project-service`；
- PostgreSQL 独立 schema/实例和 Flyway 目录；
- Asset-Workflow 只通过项目归属 API 查询，不读项目表；
- 对比单体与服务响应，稳定后切换 Gateway 路由。

### 第三步：Asset-Workflow

- 将素材、版本、授权、审核、交付提取为 `asset-workflow-service`；
- 任务/评论数据属于 project-service，审核/授权/交付数据属于 asset-workflow-service（定稿归属见 ADR-008）；
- MinIO 对象键保持兼容；
- RabbitMQ Worker 通过任务契约调用，不直接写业务表；`ai_tasks`/`suggestions` 业务事实仍由 asset-workflow-service 拥有；
- Delivery 暂留服务内，除非有独立扩缩容/故障隔离证据。

## 3. 迁移模式

```text
模块边界
→ 契约冻结
→ 独立迁移脚本
→ 数据复制/校验
→ 只读旁路
→ 小流量切换
→ Trace/指标对比
→ 保留回滚开关
→ 完成切流
→ 删除单体旧路径
```

禁止持续双写。需要同步期间，使用一次性迁移、事件补偿或可重放导出，并记录一致性窗口。

## 4. 回滚

- API 兼容窗口内，Gateway 可将流量切回单体；
- 新服务写入前必须完成数据校验；
- 数据库迁移采用向后兼容的 expand/contract 顺序；
- 回滚不得直接删除已经写入的新字段或事件；
- 每次切流都记录版本、流量、错误率、延迟和回滚命令。

## 5. 停止条件

如果服务没有独立部署/扩缩容/故障隔离价值，或者跨服务一致性成本大于收益，停止继续拆分，保留模块化单体。
