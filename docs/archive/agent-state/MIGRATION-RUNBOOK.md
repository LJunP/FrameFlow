# Legacy FrameFlow → FrameFlow Select 迁移 Runbook

## 1. 迁移原则

- 先保存、后清理；
- 不修改旧 Flyway；
- 不重写 Git 历史；
- 旧产品有 Tag 和完整文档归档；
- 新产品在独立本地分支推进；
- 迁移脚本幂等，可重复 dry-run；
- 任意源代码 Dirty 变化先快照，不能静默丢失。

## 2. 自动步骤

1. 校验包；
2. 记录 Git root、branch、HEAD、remote、status、tracked/untracked、binary diff；
3. 把 Dirty 内容复制到 `evidence/pivot/legacy-snapshot/`；
4. 对已知截断的 M01H Evidence，仅在备份后恢复 HEAD 版本；
5. 运行可用的旧基线测试；
6. 创建 `frameflow-collaboration-v0.2-legacy` Tag；
7. 创建并切换 `frameflow-select/main`；
8. 先把旧 docs 复制到 `docs/archive/frameflow-collaboration/` 并生成不可覆盖的 Snapshot Manifest；
9. 清除当前目录中的重复 Legacy 事实源，只恢复明确保留的 Identity 契约；
10. 安装 `docs/core`、`docs/contracts` 与 `.frameflow`；
11. 重新生成当前 docs 入口并写入旧任务取消登记；
12. 清理被跟踪的密钥和构建垃圾；
13. 执行 migration validation；
14. 按 Task 原子提交并完成 S0 独立 Gate。

## 3. 不自动删除的内容

- `.git`；
- 旧 Commit 和 Branch；
- 旧 Evidence；
- 已执行的 V1～V3 migration；
- 未能明确解释的用户源代码变化。

## 4. Dirty 工作树处理

- 只有已知 `evidence/m01h/mvn-verify.txt` 变化时：保存文件、保存 diff、恢复 HEAD 版本、记录原因；
- 出现其他 tracked source change：先创建本地 WIP preservation Commit 或保留 patch，再迁移；
- 出现真实 Secret：隔离、从后续 Commit 删除、记录轮换需求，但不打印 Secret。

## 5. 迁移成功

满足：

```text
legacy Tag 可解析
当前分支为 frameflow-select/main
docs/core 六本书存在
.frameflow/state.json 存在
旧未来任务不可派发
README 指向新产品
Git 历史未重写
```
