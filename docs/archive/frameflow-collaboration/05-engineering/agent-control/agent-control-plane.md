# FrameFlow 隔离式 AI Agent 调度控制面设计（PROPOSAL）

> 状态：**PROPOSAL**（待批准）。本文描述的是一个**研发辅助控制面实验**，不是 FrameFlow 用户可见业务功能，也不属于产品架构。
> 边界：本仓库采用模式 A（源码单仓库）。外部已有的多 Agent 调度工具与本控制面实验不是同一个东西：本文件只定义"如果在本项目内建设控制面"时的规则，实际派发以 `master-control-spec.md`、`task-capsule-spec.md` 和 `tasks/*.json` 为准。

## 1. 目标

以"隔离角色、最小任务包、受限工具、结构化回执"为原则，减少长期对话的上下文污染、角色漂移和越权工具调用，同时保留可审计的 AI 辅助研发效率。

```text
主控只协调
→ 子 Agent 每次 fresh context
→ 只接收最小 Task Capsule
→ 工具按任务发放最小能力
→ 结果用结构化回执返回
→ 事实、决策、建议、未知严格分离
```

## 2. 系统边界

- 控制面与业务面分离：控制面只处理脱敏任务资料，不承载用户业务数据。
- 绝对隔离：不访问 `jcm_media_api`、真实凭据、客户数据、素材、私钥、Token 或完整预签名 URL；不写业务文件。
- 两条链路不可混用：
  - 研发 Agent 调度链路：设计、开发、测试、审查、运维演练；
  - 产品 AI 链路（M7 后）：脱敏输入、Provider Adapter、Suggestion，输出必须人工确认。

## 3. 角色协议（职责分离）

| 角色 | 主要职责 | 写权限 | 禁止事项 |
|---|---|---|---|
| planner | 需求拆分、非范围、验收、风险 | 无 | 改代码、跑变更命令 |
| architect | 服务边界、数据模型、ADR | 文档建议 | 直接实现业务 |
| implementer | 实现已批准任务 | 限定写集 | 自行扩大范围、merge/push |
| test-designer | 测试矩阵、边界、故障实验 | 测试建议 | 改生产代码 |
| reliability-reviewer | 幂等、MQ、重试、Outbox、降级 | 无 | 发送真实消息、重放队列 |
| security-reviewer | 鉴权、脱敏、输入、Secret | 无 | 读取 `.env` 或凭据 |
| code-reviewer | 审查 diff 与测试缺口 | 无 | 审查自己实现的同一任务 |
| verifier | 运行 allowlist 构建/测试 | 无 | 任意 shell、外部网络 |
| ops-reviewer | Docker/K8s/Istio 配置与演练审查 | 无 | apply/删除资源 |

同一 taskId 内 implementer 不能担任 code-reviewer；同一写集不允许两个 implementer 并发写入；reviewer 建议必须标记 PROPOSAL。

## 4. 权限等级

| 等级 | 名称 | 允许行为 |
|---:|---|---|
| L0 | 只读 | 读取允许范围的代码、文档、diff 摘要 |
| L1 | 建议 | 输出计划、测试、审查建议，不落盘 |
| L2 | 隔离写入 | 仅在限定文件写集改动 |
| L3 | 受限验证 | 仅执行 allowlist 本地构建/测试命令 |
| L4 | 本地观察 | 查看 Compose、容器、队列、Trace、指标脱敏摘要 |
| L5 | 审批变更 | 迁移、消息重放、部署变更；每次人工批准 |
| L6 | 永久禁止 | 真实 Secret、生产系统、外部发布、数据导出、付款 |

## 5. Task Capsule

任务包格式以 `docs/05-engineering/schemas/task-capsule.schema.json` 为准（本设计的历史 JSON 示例已并入该 schema）。每个子 Agent 使用 fresh context，只接收 Capsule；严禁传递主对话全文、其他 Agent 推理过程、完整日志、`.env` 或完整消息 payload。

## 6. 结构化回执

```text
status       COMPLETED | BLOCKED | NEEDS_CONTEXT | FAILED
facts        可验证事实 + evidenceRef
findings     严重性 + 陈述 + 证据
proposals    建议（不能自动成为决策）
unknowns     未验证项
testResults  命令 + exitCode + 报告引用
changedPaths 实际变更路径
```

四类信息不得混淆：FACT / DECISION / PROPOSAL / UNKNOWN。

## 7. 落地前置条件

- 先完成 P0-Prep 文档收敛门禁；
- 调度以 `tasks/*.json` 为准，不解析自然语言阶段描述；
- 控制面实验不得阻塞产品主线（P0～M8）；
- 实际研发流程的冲突优先级以 `master-control-spec.md` 为准：

```text
治理规则 > PRD/ADR/权威架构 > Task Capsule > Stage Prompt > Agent 建议
```
