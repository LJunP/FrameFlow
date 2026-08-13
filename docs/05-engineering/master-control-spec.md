# FrameFlow 总控开发规范（供调度工具使用）

> 用途：多 Agent 调度工具在执行 FrameFlow 开发时必须遵守的总控规则。当前开发进度为 0，本文件只定义规则。

## 1. 权威文档

| 主题 | 权威文档 |
|---|---|
| 产品范围 | `docs/01-product/FrameFlow-PRD.md` |
| 架构与 ADR | `docs/02-architecture/FrameFlow-概要设计.md`、`docs/02-architecture/adr/` |
| 模块边界 | `docs/02-architecture/module-boundaries-and-dependency-rules.md` |
| 服务与数据所有权 | `docs/02-architecture/service-boundaries-and-data-ownership.md` |
| 数据与实现约束 | `docs/03-data/FrameFlow-详细设计.md` |
| API/契约/事件 | `docs/04-api/` |
| 任务包顺序 | `docs/05-engineering/development-plan-p0-m17.md` |
| 任务包格式 | `docs/05-engineering/task-capsule-spec.md` |
| Requirement/Test 注册表 | `docs/05-engineering/registries/requirements.json`、`docs/05-engineering/registries/test-cases.json` |
| 测试与验收 | `docs/06-testing/` |
| 安全与运维 | `docs/07-operations/` |
| 证据 | `docs/09-delivery/evidence-index.md` |

## 2. 执行规则

```text
1. product-mainline（P0～M8）严格按 development-plan-p0-m17.md 顺序执行，不跳步；
   engineering-lab（M9～M17）仅在被选中时按依赖拓扑执行，未选阶段不阻塞产品主线。
2. 每个任务包执行前先核验仓库事实，输出 FACT/DECISION/PROPOSAL/UNKNOWN。
3. 先修改权威文档/ADR，再写代码；代码必须配套测试和证据。
4. 模块之间不得直接访问其他模块 Repository、Mapper、Entity 或表。
5. Controller 不直接写 SQL；领域规则不放 Controller。
6. AI 输出只能作为 suggestion，人工确认后才能写入正式业务数据。
7. RabbitMQ 只负责待执行命令与结果回传；Kafka 只负责 M11 后的领域事件。
8. MVP 事务主库从 P0 起为 PostgreSQL；MySQL 只在 M13 的 identity-service 使用，不做双写。
9. 不引入技术边界外的中间件、框架或目录。
10. 不可逆动作（迁移、删除、发布、重放、提交到远端）必须人工审批。
11. 每个任务包完成后回写 project-status.md 与 evidence-index.md。
12. 无证据的结论标记"待补充"，不得宣称完成。
13. 调度器必须对 Task Capsule 与 Capability Grant 做 Schema、注册表、前置任务和交叉引用校验；Grant 只能收窄 Capsule 权限。
14. readSet/writeSet 路径须解析到规范仓库相对路径并拒绝逃逸；命令 exact/prefix 须按任务协议的词边界匹配，禁止经 shell 解释器绕过。
15. `.zcode/` 可作为用户本地工具目录保留，但它被 `.gitignore` 和正式打包流程排除，且不属于 FrameFlow 权威输入、实现或证据。
16. 执行者的 writeSet 不得包含当前 Task Capsule；执行者不得自改任务状态，调度器仅在 Receipt、验收、证据和 changedPaths 校验通过后回写 Task/catalog/generated 状态。
17. `FF-PP-001` 是唯一一次 pre-Git/control-plane bootstrap 例外：不补造 Grant/Receipt，无法重建 exact diff/budget，其公开边界以 `evidence/prep/p0-prep-bootstrap-audit.md` 为准；该例外不得用于 P0 或后续任务。
```

## 3. 安全红线

```text
不访问 jcm_media_api 的代码、配置、数据库、消息、Webhook、测试环境或凭据
不读取或提交 .env、Token、API Key、私钥、真实客户数据、素材或完整预签名 URL
所有开发使用 FrameFlow 自己的隔离本地环境
不把本地实验、压测或故障演练表述为生产经验
```

## 4. 阶段门禁

每个阶段只有在满足以下条件时才允许标记完成：

```text
任务包全部验收通过
自动化测试通过且结果可复现
迁移/配置可验证
真实命令与原始输出已回写证据索引
文档与 README 同步更新
未完成项和限制已记录
```

## 5. 完成与交付

最终交付物由 M17 定义，包括可复现 README、业务闭环演示、架构图、OpenAPI、测试/压测/JVM/K8s/Istio 证据和面试材料。所有内容必须可追溯到仓库文件与真实运行记录。
