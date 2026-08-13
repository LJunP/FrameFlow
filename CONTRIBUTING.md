# 贡献与开发约定

## 1. 仓库模式

- 模式 A：源码单仓库。docs/、代码、deploy/ 与 evidence/ 全部在 FrameFlow 仓库内。
- 当前阶段：开发前治理基线（业务开发进度 0）；P0-Prep 真实验证通过、用户分别授权 Git 初始化与执行 P0、环境预检通过后，调度器才把 P0 标记为可派发；Git 基线是派发后的首个受审批动作。
- `.zcode/` 允许作为被忽略的本地工具目录保留，但不是权威计划、任务输入或证据，不得强制加入 Git 或进入正式交付包。

## 2. 开发入口

- 任务包（JSON）：`docs/05-engineering/tasks/`
- 任务包规范：`docs/05-engineering/task-capsule-spec.md`
- 开发计划与门禁：`docs/05-engineering/development-plan-p0-m17.md`
- 总控规则：`docs/05-engineering/master-control-spec.md`

## 3. 提交流程

- 每个任务包单独 feature 分支；实现与审查角色分离。
- 提交格式：`feat:`、`fix:`、`test:`、`docs:`、`refactor:`、`ops:`。
- 提交必须关联 Task ID，证据必须关联 Evidence ID。
- 不提交 `.env`、生成物、日志、真实素材、密钥或 IDE 文件。

## 4. 文档与证据

- 先改权威文档/ADR，再改代码和派生文档。
- 每个任务包完成后回写 `docs/09-delivery/evidence-index.md` 与 `docs/00-governance/project-status.md`。
- 没有真实命令、原始输出或报告的内容标记"待补充"，不得宣称完成。

## 5. 安全红线

- 不访问 `jcm_media_api` 的代码、配置、凭据或测试环境。
- 不提交 Token、API Key、客户数据、真实素材或完整预签名 URL。
- 本地实验、压测和故障演练不表述为生产经验。
