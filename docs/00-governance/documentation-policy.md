# 文档治理规则

## 1. 文档状态标签

每份文档和每条重要结论必须能区分：

- **FACT**：已由仓库、命令、测试或运行记录验证的事实。
- **DECISION**：已经批准、用于指导实现的决策。
- **PLAN**：计划中的阶段目标，尚未实现。
- **PROPOSAL**：待批准的方案，不得当成既定架构。
- **UNKNOWN**：尚未决定或没有证据。
- **EVIDENCE**：可复现的命令、原始输出、报告或故障记录。

当前仓库的 P0 工程基座已有代码和运行证据；M01 及后续业务技术仍只能标记为 `PLAN`，不能写成 `FACT`。

## 2. 单一事实来源

| 事实 | 权威文档 |
|---|---|
| 产品范围、角色、业务规则 | `docs/01-product/FrameFlow-PRD.md` |
| 总体架构、模块和服务决策 | `docs/02-architecture/FrameFlow-概要设计.md`、ADR |
| 模块边界和依赖规则 | `docs/02-architecture/module-boundaries-and-dependency-rules.md` |
| 服务边界和数据所有权 | `docs/02-architecture/service-boundaries-and-data-ownership.md` |
| 领域对象、表约束和补偿 | `docs/03-data/FrameFlow-详细设计.md` |
| API 及服务契约 | `docs/04-api/` |
| 阶段顺序、任务包和门禁 | `docs/05-engineering/development-plan-p0-m17.md`、`docs/05-engineering/tasks/*.json` |
| 本地运行方式 | `docs/05-engineering/local-development.md` |
| 测试和验收 | `docs/06-testing/` |
| 安全、故障和平台运行 | `docs/07-operations/` |
| 学习节奏 | `docs/08-learning/48-week-plan.md`（历史存档在 `legacy/`，非权威） |
| 实际完成证据 | `docs/09-delivery/evidence-index.md` |

README 只做入口和当前状态摘要；学习路线不能覆盖 ADR；AI Prompt 不能创造新的架构事实。

## 3. 变更规则

1. 先修改权威文档，再修改代码和派生文档。
2. 架构取舍、技术选型、服务边界、数据所有权和运行环境职责必须写 ADR。
3. API 或事件变更必须更新契约、兼容性说明和测试。
4. 数据库结构变更必须通过 Flyway/Liquibase 迁移脚本，不接受手工改库作为交付。
5. Kubernetes、Helm、Istio 配置变更必须有验证命令和回滚说明。
6. 每个阶段完成时更新 `project-status.md` 和 `evidence-index.md`。
7. 计划、建议和未知不得写成已实现事实；本地实验不得写成生产经验。

## 4. 路径规则

只使用仓库当前数字化目录：

```text
docs/00-governance
docs/01-product
docs/02-architecture
docs/03-data
docs/04-api
docs/05-engineering
docs/06-testing
docs/07-operations
docs/08-learning
docs/09-delivery
```

禁止在 Prompt、证据或新文档中引用不存在的 `docs/architecture`、`docs/api`、`docs/database`、`docs/testing` 等平行目录。

## 5. 安全与隐私

所有文档不得包含：

- 真实密钥、Token、私钥、完整预签名 URL；
- 客户数据、真实媒体和内网地址；
- `jcm_media_api` 的配置、数据库、消息、Webhook 或测试环境信息；
- 未脱敏的外部 AI payload。

## 6. Prompt 使用规则

AI 开发 Prompt 只能：

- 引用本政策列出的权威文档；
- 明确 scope、non-goals、write set、命令白名单和验收标准；
- 要求输出实际命令、测试结果和未完成项；
- 遇到 UNKNOWN 时停止猜测并提出决策问题。

Prompt 不得自行创建服务、数据库、目录、密钥或部署方式。
