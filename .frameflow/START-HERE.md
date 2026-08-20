# START HERE — 单会话迁移与自治开发入口

## 1. 目标

在一个持续会话中完成：

```text
验证交接包
→ 审计旧仓库
→ 保存旧基线与异常工作树
→ 创建 legacy Tag 与新产品分支
→ 安装六本权威手册和机器计划
→ 归档旧产品事实源
→ 迁移角色与工程状态
→ 按 Master Plan 实现全部本地 MVP
→ 独立验证每个 Task 和 Stage
→ 生成最终交付、运行说明和干净源码包
```

用户不需要逐任务确认。可逆的本地决策由执行契约预先授权。

## 2. 第一次执行的固定顺序

先只校验并读取控制面，不要立即覆盖旧文档：

```bash
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/verify_pack.py
```

然后完整读取：

```text
execution/AUTONOMOUS-EXECUTION-CONTRACT.md
execution/DECISION-POLICY.yaml
execution/MASTER-PLAN.yaml
execution/COMPLETION-CRITERIA.yaml
books/BOOK-01 ... BOOK-06
migration/MIGRATION-RUNBOOK.md
```

按 `MASTER-PLAN.yaml` 完成 `FF-PIV-001` 和 `FF-PIV-002`，先保存当前脏工作树、建立旧产品基线 Tag，并创建新产品分支。只有基线可恢复后，才按以下顺序迁移文档：

```bash
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/migrate_legacy_docs.py --dry-run
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/migrate_legacy_docs.py --apply
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/bootstrap_repository.py --dry-run
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/bootstrap_repository.py --apply
python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/validate_repository.py --phase migrated
```

`migrate_legacy_docs.py` 必须先于 `bootstrap_repository.py`，否则可能先覆盖再归档旧事实源。两个脚本都是幂等设计；迁移完成后重复执行应返回 NOOP 或只补齐缺失安装项。

## 3. 安装后的正式位置

Bootstrap 完成后：

```text
docs/core/                 六本权威手册
docs/contracts/            产品与 AI 机器契约
.frameflow/master-plan.yaml
.frameflow/decision-policy.yaml
.frameflow/completion-criteria.yaml
.frameflow/state.json
.frameflow/tasks/
evidence/pivot/
```

包目录保持只读，用于校验来源；安装后正式事实源为 `docs/core/`、`docs/contracts/` 与 `.frameflow/`。

## 4. 执行循环

对每个 Task：

```text
生成 Task Capsule
→ Baseline
→ 实现
→ 自测
→ 独立 worktree 验证
→ Gate 判定
→ 本地 Commit
→ 更新 state.json
→ 自动选择下一 Task
```

如果 Task 两次失败：使用 Master Plan 中的 Fallback 缩小目标。不要向用户索要普通技术选择。

## 5. 终点

只有 `COMPLETION-CRITERIA.yaml` 全部满足时，才写入：

```text
.frameflow/state.json.status = LOCAL_MVP_COMPLETE
```

并生成：

```text
FINAL-HANDOVER.md
RUNBOOK.md
evidence/final/final-verdict.md
dist/frameflow-select-source.zip
```

真实市场试点保持 `EXTERNAL_VALIDATION_PENDING`，不影响本地 MVP 交付。
