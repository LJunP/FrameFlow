# FF-PP-001 一次性 P0-Prep Bootstrap 审计披露

> 状态：ACCEPTED ONE-TIME EXCEPTION
> 日期：2026-08-13
> 唯一适用对象：`FF-PP-001`
> 性质：透明边界记录；不是 Capability Grant，不是 Agent Receipt，也不是历史执行证明

## 1. 为什么存在例外

`FF-PP-001` 的工作目标之一就是建立 Task Capsule、Capability Grant、Agent Receipt、注册表、索引与门禁规则。执行这批收敛工作时，工作区尚未初始化 Git，也不存在已经生效的控制面或不可变前置 baseline。因此当时没有能够事先签发的 Capability Grant，也没有能够按后来 Schema 回写的 Agent Receipt。

不得为补齐形式而倒填或伪造 Grant/Receipt。当前文件内容、文件时间戳和对话记录都不能替代不可变 Git 基线。

## 2. 无法事后可靠重建的内容

以下内容属于明确 UNKNOWN，不能被 37/37 PASS 推导出来：

- bootstrap 前后的 exact diff；
- 完整且可密码学验证的 changedPaths；
- 历史执行的精确命令清单、命令次数与顺序；
- 精确开始/结束时间和耗时；
- 当时是否满足后来定义的 `maxFiles`、`maxCommands`、`maxMinutes`；
- 一份真实存在于执行前并约束执行过程的 Capability Grant；
- 一份由执行 Agent 在任务结束时产生并通过 Schema 的 Agent Receipt。

本项目不会声称这些内容已被验证，也不会创建带历史时间的替代对象。

## 3. 当前确实验证过的内容

2026-08-13 运行 `python3 scripts/validate_p0_prep.py`，验证的是修复后的当前工作区状态：

- `EV-FF-PP-001-01`：一致性 5/5 PASS；
- `EV-FF-PP-001-02`：Schema、编号、依赖与引用 12/12 PASS；
- `EV-FF-PP-001-03`：阶段技术基线 5/5 PASS；
- `EV-FF-PP-001-04`：P0 任务可复现性 8/8 PASS；
- `EV-FF-PP-001-05`：包卫生与本地工具隔离 7/7 PASS。

合计 37/37 PASS。原始文本位于 `evidence/prep/`。这些结果证明当前基线满足对应检查，不证明 bootstrap 历史执行过程符合后来才建立的控制面协议。

## 4. 已知修复范围

依据本次工作记录，可确认修复至少涉及以下范围；这是一份透明的已知范围，不是可密码学证明的完整 changedPaths：

- 根目录治理入口与忽略规则；
- `docs/00-governance/` 的状态、启动、检查与打包口径；
- `docs/03-data/` 与 `docs/04-api/` 的 M01 身份、Token、权限、幂等和错误契约收敛；
- `docs/05-engineering/` 的 Schema、任务包、注册表、开发计划、总控、CI、catalog 与 generated index；
- `docs/09-delivery/evidence-index.md`；
- `scripts/validate_p0_prep.py`；
- `evidence/prep/` 下的门禁输出与本披露文件。

由于没有前置 Git baseline，本清单不能用于声称其他路径一定未被修改。

## 5. 为什么 FF-PP-001 保持 DONE

治理决策接受这个不可复用的 bootstrap 例外：`FF-PP-001` 以当前态 37/37 PASS、五份原始输出和本文件公开的不可恢复边界保持 `DONE`。这里的 `DONE` 仅表示 P0-Prep 当前基线门禁已经闭合，不表示存在标准 Grant/Receipt，不表示 exact diff 或 budget 已被验证。

该例外不授权 Git 初始化或 P0 执行；`FF-P0-001` 在用户对这两个动作分别明确授权前继续保持 `NOT_READY`。

## 6. 首个 Git Baseline 的后继锚点要求

用户分别明确授权 Git 初始化与执行 P0、环境预检通过且调度器将 P0 标记为可派发并签发受限 Grant 后，P0 必须：

1. 把建立首个 Git baseline 作为 P0 派发后的首个受审批动作；
2. 在 P0 Receipt/Evidence 中记录首个 commit hash，作为 PP 当前态之后的后继锚点；PP 的五份 pre-Git 原始证据保持不变，不事后重写或伪装为该 commit 的工作树；
3. 使用 `git ls-files` 证明 `.zcode/`、密钥、环境文件、构建产物和平台垃圾未被跟踪；
4. 使用 `git check-ignore` 证明本地 `.zcode/` 被忽略；
5. 检查 `git archive` 清单，证明 `.zcode/` 不进入正式归档；
6. 保留本文件，不把首个 commit 误述为 bootstrap 前置基线或 PP 原始证据所属 commit。

首个 commit 只能锚定修复后的当前状态，不能追溯证明历史 exact diff、命令或 budget。

## 7. 不可复用规则

本例外只适用于 `FF-PP-001`，不得复制到 P0、M01 或任何后续任务。`FF-P0-001` 及以后缺少事前 Capability Grant、合法 Agent Receipt、writeSet/changedPaths 校验、预算记录或证据回写中的任一项，都不得标记 `DONE`。
