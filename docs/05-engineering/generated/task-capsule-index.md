# 任务包索引（generated）

> 本文件由 `docs/05-engineering/tasks/**/*.json` 生成；当前为按实际文件手动重建的可验证快照。`FF-M01-001=DONE`、`FF-M01H-001=DONE`；M02 Contract Gate 与新前端任务仍不可派发。

## 实际存在且可校验的任务文件

| Task ID | 阶段 | 轨道 | 状态 | 文件 |
|---|---|---|---|---|
| FF-PP-001 | P0-Prep | prep | DONE | `tasks/PP/FF-PP-001.json` |
| FF-P0-001 | P0 | product-mainline | DONE | `tasks/P0/FF-P0-001.json` |
| FF-M01-001 | M01 | product-mainline | DONE | `tasks/M01/FF-M01-001.json` |
| FF-M01F-001 | M01-F | product-mainline | NOT_READY | `tasks/M01F/FF-M01F-001.json` |
| FF-M01H-001 | M01-H | product-mainline | DONE | `tasks/M01H/FF-M01H-001.json` |
| FF-M02-001 | M02 | product-mainline | DRAFT | `tasks/M02/FF-M02-001.json` |
| FF-M02-002 | M02 | product-mainline | NOT_READY | `tasks/M02/FF-M02-002.json` |
| FF-M02-003 | M02 | product-mainline | NOT_READY | `tasks/M02/FF-M02-003.json` |
| FF-M02-004 | M02 | product-mainline | NOT_READY | `tasks/M02/FF-M02-004.json` |
| FF-M04F-001 | M04-F | product-mainline | DRAFT | `tasks/M04F/FF-M04F-001.json` |
| FF-M08F-001 | M08-F | product-mainline | DRAFT | `tasks/M08F/FF-M08F-001.json` |

## PLANNED（规划中，任务文件尚未创建，禁止派发）

```text
FF-M03-001  FF-M04A-001  FF-M04B-001  FF-M05-001  FF-M06-001
FF-M07-001  FF-M08-001   FF-M09-001   FF-M10-001
FF-M11-001  FF-M12A-001 FF-M12B-001 FF-M12C-001   FF-M12D-001
FF-M13-001  FF-M14-001  FF-M15-001  FF-M16-001    FF-M16G-001
FF-M17-001
```

## 派发规则

- 只有 `status=AVAILABLE/READY_FOR_DISPATCH` 且前置任务 `DONE` 的任务允许派发；
- `DRAFT`：边界/依赖未定稿；`NOT_READY`：契约、前置、授权或环境任一未满足；`CONTRACT_READY`：契约已定稿但其他前置未满足。三者均不得派发；
- `PLANNED` 条目不是可派发输入，仅表示计划；
- 每个任务派发前必须生成对应 Capability Grant，执行后必须回写 Agent Receipt；执行者不得修改当前 Task Capsule，状态由调度器校验 Receipt 后回写。
- `FF-PP-001` 是唯一一次 pre-Git/control-plane bootstrap 例外；其不可恢复边界记录在 `evidence/prep/p0-prep-bootstrap-audit.md`，不得复制到后续任务。
