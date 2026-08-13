# FrameFlow 任务包规范（Task Capsule v2）

> 状态：DECISION（已批准，v2 安全派发协议）。机器可读格式以 `schemas/` 下三个 Schema 为唯一权威；本文件是可读说明。

## 1. 权威结构

```text
docs/05-engineering/
├── schemas/
│   ├── task-capsule.schema.json     任务包协议 v2
│   ├── capability-grant.schema.json 能力授权（能做什么）
│   └── agent-receipt.schema.json    执行回执（实际做了什么）
├── tasks/
│   ├── PP/FF-PP-001.json           P0-Prep 文档收敛
│   ├── P0/FF-P0-001.json
│   ├── M01/FF-M01-001.json
│   └── ...
├── registries/
│   ├── requirements.json           Requirement ID 注册表
│   └── test-cases.json             Test ID 注册表
└── generated/
    └── task-capsule-index.md       由任务文件生成，不手写
```

最终 Agent 输入 = Control Prompt + Stage Prompt + Task Capsule + Capability Grant；冲突优先级：

```text
治理规则 > PRD/ADR/权威架构 > Task Capsule > Stage Prompt > Agent 建议
```

## 2. 编号体系（方案 A，定稿）

```text
Task ID          FF-PP-001、FF-P0-001、FF-M01-001、FF-M04A-001、FF-M12C-001、FF-M16G-001
Requirement ID   REQ-<DOMAIN>-<SEQ>          例如 REQ-IAM-001
Acceptance ID    AC-FF-M01-001-01
Test ID          TEST-FF-M01-001-01
Evidence ID      EV-FF-M01-001-01
Capability Grant GRANT-<TASK>-<SEQ>
Receipt          RCPT-<TASK>-<SEQ>
```

M 后两位阶段编号（M01、M04A、M16G），便于排序和机器解析。

## 3. 关键字段语义（v2 新增）

| 字段 | 语义 |
|---|---|
| `status` | `NOT_READY`（契约/前置/授权/环境未满足）→ `CONTRACT_READY`（API/契约已定稿但仍不可派发）→ `AVAILABLE`/`READY_FOR_DISPATCH`（可派发）→ `IN_PROGRESS` → `DONE` |
| `readSet` / `writeSet` | 读写权限分离；实现者读取权威文档不需要获得修改权；writeSet 不含权威契约目录或当前 Task Capsule 自身 |
| `allowedCommands` | 命令白名单，`exact` 或 `prefix` 策略；名单外命令默认拒绝 |
| `networkPolicy` | 外部网络/本地/包仓库访问策略 |
| `toolCapabilities` | 显式 MCP/宿主工具白名单 |
| `prerequisiteTaskIds` | 前置任务，未完成禁止派发 |
| `requirementIds` / `testIds` | 必须在对应注册表存在 |
| `acceptance[].testIds/evidenceIds` | 每条验收显式映射，可机器检查 |
| `approvalPoints[].approvalType` | irreversible / destructive / release / security / local 分级 |

## 4. 路径与命令约束

```text
路径必须为仓库相对路径；禁止绝对路径、Windows 盘符、反斜杠、空路径段、任意位置的 .. 路径段和 .git 内容
writeSet 必须独立声明，不允许用 scope 混用读与写
当前 Task Capsule、其状态和授权边界由调度器拥有：执行者不得把任务包自身放入 writeSet，也不得自改状态；调度器只能在校验 Receipt、验收和证据后回写状态
命令必须逐条声明；不允许 "bash -c <任意>" 形式的通配执行
命令禁止换行、命令替换、管道、重定向和 shell 控制操作符；组合流程必须固化为 writeSet 内的受审脚本
prefix 只在命令完全相等或白名单前缀后紧跟一个 ASCII 空格时匹配，不得使用普通字符串 startsWith
Capability Grant 的路径、命令、网络和工具权限必须是 Task Capsule 的子集；宿主调度器负责运行时强制执行
budget 必须为正数；超预算必须上报
```

`requirementIds` 必须存在于 `registries/requirements.json`，`testIds` 及验收映射中的 Test ID 必须存在于 `registries/test-cases.json`。验收引用的 Evidence ID 必须在同一任务包的 `evidence` 数组中存在；Evidence 输出必须位于 `writeSet`。

## 5. 角色与职责分离

| 角色 | 职责 | 禁止 |
|---|---|---|
| planner | 拆分任务包、定义范围与验收 | 改代码、跑变更命令 |
| architect | 设计边界、写 ADR | 直接实现业务 |
| implementer | 实现已批准任务包 | 自行扩大范围、审批自己 |
| verifier | 运行 allowlist 构建/测试并回写结果 | 修改业务设计 |
| reviewer | 独立审查 diff、测试和契约 | 审查自己实现的同一任务 |

`roleType`（planner/architect/implementer/verifier/reviewer）+ `specialty`（general/test/security/reliability/code/operations）兼容控制面角色枚举（test-designer → specialty=test 等）。

## 6. 事实分层

```text
FACT        可验证的事实（代码、测试、命令输出）
DECISION    已批准决策（ADR、PRD）
PROPOSAL    Agent 建议，需要人工批准
UNKNOWN     未验证或未决定，需要上报
```

禁止把 PROPOSAL 或 UNKNOWN 当 FACT 使用。

## 7. 完成定义

只有以下全部成立才标记 `DONE`：

```text
验收全部 PASS 且与 Test/Evidence 显式映射
回执（Receipt）通过 agent-receipt.schema.json 校验
changedPaths 全部在 writeSet 内
evidence 已回写 evidence-index
任务状态、目录和 generated 索引已同步
未发现超预算、未上报 unknown 或范围外变更
```

### 7.1 FF-PP-001 一次性 Bootstrap 例外

`FF-PP-001` 同时建立了 Git 前治理基线和上述控制面协议，因此执行当时不存在可签发的 Capability Grant、可校验的 Agent Receipt 或不可变前置 baseline。不得事后补造这些历史对象，也不得把当前文件状态冒充 exact changedPaths、命令计数、耗时或 budget 证明。

该任务仅依据五类当前态机器门禁 37/37 PASS、原始输出、公开的不可恢复边界以及 [P0-Prep Bootstrap 审计披露](../../evidence/prep/p0-prep-bootstrap-audit.md) 保持 `DONE`。这证明的是修复后基线满足门禁，不证明历史过程符合后来才建立的 Grant/Receipt 协议。PP 证据保持不可变的 pre-Git 事实，不在事后重写；首次获授权的 P0 Git baseline 由 P0 Receipt/Evidence 记录为对该当前态的后继锚点，并验证 `.zcode` 未跟踪/未归档，不得声称它能倒推出 bootstrap exact diff 或预算。

此例外仅限 `FF-PP-001`、不可复制、不可作为先例。`FF-P0-001` 及以后必须完整执行正常完成定义，缺少合法 Grant 或 Receipt 时不得标记 `DONE`。
