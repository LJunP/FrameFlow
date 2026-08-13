# 交付包与打包规范

> 状态：DECISION。适用于所有对外交付的压缩包/归档。

## 1. 打包方式

- 正式交付包使用 `git archive` 或等价干净打包流程，**禁止从 macOS Finder 直接压缩目录**；
- 打包前必须存在 Git 提交基线，包内容可追溯到 commit/hash。

```bash
git archive --format=zip --output=frameflow-<commit>.zip HEAD
```

## 2. 打包前自动检查（任一失败即不发包）

```text
[ ] 无 .DS_Store / __MACOSX / 平台垃圾
[ ] 正式交付包中无 .zcode（本地工作区允许保留，见第 3 节）
[ ] 无个人主目录绝对路径（扫描 /Users/、/home/）
[ ] 无 .env、密钥模式、Token、真实素材
[ ] 非 ASCII 文件名设置了 UTF-8 flag（zip 工具检查）
[ ] README 浏览声明已包含（本仓库不创建 LICENSE，保留所有权利）
[ ] 依赖清单和第三方许可可追溯
```

## 3. 仓库卫生规则

- `.zcode/` 是用户明确保留的本地工具目录，允许存在于工作区；它不是 FrameFlow 的产品源码、权威计划、任务状态或验收证据；
- `.gitignore` 已排除 `.zcode/`、`__MACOSX/`、`.DS_Store`、`target/` 等；`.zcode/` 不得被强制加入 Git，也不得进入正式交付包；
- 文档与任务发生冲突时，以 `docs/` 下的权威文档和结构化任务包为准，不以 `.zcode/` 内容判断当前技术基线或阶段状态；
- P0-Prep 在 Git 初始化之前先验证 `.gitignore` 与打包规则明确排除 `.zcode/`；建立 Git 基线后再验证它未被跟踪、未进入归档。任何阶段都不得以“本地目录不存在”作为门禁；
- legacy 目录（`docs/08-learning/legacy/`）不进入精简交付包，只保留指向说明；
- 证据原始文件（压测输出、JFR、GC 日志）使用受控目录 `evidence/`，不进入源码包。

## 4. 使用许可（DECISION）

- 已定：**不创建 LICENSE 文件，保留所有权利**；
- `README.md` 顶部已声明：仅限浏览与学习参考，禁止复制、修改、商用、二次分发；
- 对外分发包必须携带该声明；如需使用、合作或转载，必须联系项目所有者获得书面许可；
- 将来若改变许可策略，必须由项目所有者明确决定并更新本政策。
