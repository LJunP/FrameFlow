# 模块边界与依赖规则

> 状态：DECISION / PLAN。模块化单体阶段的内部约束，必须在代码中由架构测试验证。

## 1. 目标

模块化单体不是把所有类放在一个包里，而是在同一部署单元内保留可验证的领域边界。模块可以共享运行时和 PostgreSQL 实例，但不能共享内部实现细节。

## 2. 模块职责

| 模块 | 允许拥有 | 对外暴露 |
|---|---|---|
| `identity` | 用户、团队、成员、角色、认证、Refresh 会话、Identity 写请求幂等记录 | 身份查询、成员/角色校验 Port |
| `project` | 客户、项目、Brief、项目成员 | 项目上下文查询、Brief 应用 API |
| `workflow` | 分镜、任务、评论、审核 | 任务和审核应用 API |
| `asset` | 素材、素材版本、授权、对象键 | 素材版本和授权 Port |
| `ai` | AI 任务、Provider、Suggestion | 异步任务入口、Suggestion 确认 API |
| `delivery` | 交付包、交付项、确认、锁定 | 交付应用 API |
| `governance` | MVP `audit_logs`、基础站内 `notifications`、技术证据 | AuditPort、NotificationPort、审计/通知查询 API；是这两张表的唯一 Mapper/迁移所有者 |

## 3. 依赖规则

允许：

```text
Controller → Application Service → Domain
Application Service → Port
Infrastructure → Port 实现
模块 A → 模块 B 的公开 Application API / Port / Event
```

禁止：

```text
Controller 直接访问 Mapper/Repository
模块 A 直接访问模块 B 的 Repository、Mapper、Entity 或数据库表
Domain 依赖 Spring、MyBatis、Redis、RabbitMQ、MinIO SDK
共享跨域业务 Entity、共享可变 DTO、跨模块静态工具持有业务状态
```

`shared-kernel` 只允许放极少量技术原语，例如 ID、分页值对象和错误码接口；不得放 Project、Asset、User 等跨域实体。

## 4. 端口与事件

- 同步读取：优先使用模块公开的 Query/Application Port。
- 跨模块写入：由拥有数据的模块执行，调用方只能提交命令。
- 解耦通知：使用领域事件；事件只描述已发生事实，不暴露内部数据库结构。
- 业务模块不得直接访问 `audit_logs`/`notifications`；关键变更通过 AuditPort 在明确的事务边界记录，基础站内通知通过 NotificationPort 或 post-commit 应用事件创建。
- 第三方基础设施：通过 StoragePort、AiProviderPort、TaskPublisherPort 隔离。

## 5. 数据边界

- 模块共享 PostgreSQL 实例，但每个模块只能访问自己的表。
- 外键只允许在同一数据所有权边界内；跨模块用 ID 和应用校验。
- 所有写操作通过拥有模块的 Service 事务完成。
- 任何跨模块直接 SQL、Mapper 或 Repository 引用都必须阻断构建。

## 6. 架构测试要求

使用 ArchUnit 或等价架构测试验证：

1. `..controller..` 不依赖 `..infrastructure..`；
2. 模块不得依赖其他模块的 `..repository..`、`..mapper..`、`..entity..`；
3. Domain 包不依赖 Spring、MyBatis、Redis、RabbitMQ、MinIO；
4. 共享内核不依赖任何业务模块；
5. 每个模块的公开接口位于明确的 `api` 或 `application.port` 包。

架构测试失败时，不得以关闭规则、放宽包扫描或复制 DTO 的方式绕过；必须修改依赖方向或记录 ADR。

## 7. 进入微服务的门禁

模块只有在满足以下条件后才可成为独立服务候选：

- 数据所有权明确；
- 无反向 Repository 依赖；
- 对外 API/事件契约明确；
- 有独立部署、扩缩容或故障隔离理由；
- 单体内有覆盖该边界的测试和演示；
- 有数据迁移、兼容窗口和回滚方案。
