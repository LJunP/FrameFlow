# 消息目录（Command / Event / Result）

> 状态：DECISION。消息命名统一为三类：RabbitMQ **command**（待执行任务）、**result**（任务结果回传）、Kafka **event**（已发生领域事件，M11 后）。消息 schema 以 `docs/04-api/schemas/` 下的 JSON Schema 为准（派发前补齐）。

## 1. 命名规范

| 类型 | 格式 | 示例 | 通道 |
|---|---|---|---|
| command | `动作.对象.vN` | `analyze.asset-version.v1` | RabbitMQ |
| result | `对象-分析.completed/failed.vN` | `asset-analysis.completed.v1` | RabbitMQ |
| event | `对象.动作.vN` | `asset.version.created.v1` | Kafka（M11 后） |

RabbitMQ 是命令/任务队列，不承载"已发生事件式"名称；Kafka 只承载领域事件。

## 2. Producer ID（统一小写 service id）

```text
identity-service
project-service
asset-workflow-service
ai-worker
```

禁止在事件/消息中使用 Asset-Workflow、Project、Identity 等大小写混合名称。

## 3. RabbitMQ Command Catalog（M6 起）

| command | producer | consumer | 幂等键 |
|---|---|---|---|
| `analyze.asset-version.v1` | asset-workflow-service | ai-worker | `taskId` |

## 4. RabbitMQ Result Catalog（M6/M7 起，AI 回传通道定稿）

| result | producer | consumer | 语义 | 幂等键 |
|---|---|---|---|---|
| `asset-analysis.completed.v1` | ai-worker | asset-workflow-service | 分析成功，业务服务幂等更新 suggestion | `taskId` |
| `asset-analysis.failed.v1` | ai-worker | asset-workflow-service | 失败（可重试/DLQ） | `taskId` |

## 5. Kafka Event Catalog（M11 后）

| event | producer | 消费者示例 | 语义 |
|---|---|---|---|
| `asset.version.created.v1` | asset-workflow-service | Audit、AI、Search | 新版本已创建 |
| `asset.review.approved.v1` | asset-workflow-service | Audit、Notification | 审核已通过 |
| `delivery.confirmed.v1` | asset-workflow-service | Audit、Analytics | 交付已确认 |
| `project.archived.v1` | project-service | Audit、Search | 项目已归档 |
| `identity.member.removed.v1` | identity-service | Project、Asset-Workflow | 成员已移除 |

## 6. 通用 Envelope

```json
{
  "messageType": "command | result | event",
  "messageName": "analyze.asset-version.v1",
  "messageId": "uuid",
  "schemaVersion": 1,
  "occurredAt": "RFC-3339",
  "producer": "asset-workflow-service",
  "correlationId": "uuid",
  "payload": {}
}
```

不得放密码、Token、原始文件内容、完整预签名 URL 或未脱敏客户信息。

## 7. 消费约束

- 消费者按 `messageId`/`taskId`/`eventId` 去重，业务效果幂等；
- 消费失败有限重试后进入 DLQ/DLT；人工重放有权限和审计；
- Kafka 事件是最终一致投影，不修改 PostgreSQL 核心事实；
- schemaVersion 增加时保留旧消费者兼容窗口。
