# 项目状态

> 本文只记录当前可验证事实、已批准决策和明确的下一步，不把设计目标写成已实现能力。

## 1. 当前已验证事实

- 当前处于**开发前治理基线阶段**，已有文档、任务协议、P0-Prep 校验脚本与原始证据，正式业务代码尚未开始。
- 已重建文档事实源：README、三书、治理、API、工程、测试、运维、证据索引和 ADR-001～011。
- 当前没有任何 `pom.xml`、`src/`、迁移、Docker Compose、Dockerfile、Kubernetes 清单、业务测试实现或 Git 提交。
- 当前没有可验证的启动命令、测试结果、压测结果、JVM 采样、Kubernetes 发布记录或 Istio 实验记录。
- 工作区不是 Git 仓库；Git 初始化将由后续正式开发流程执行。
- `.zcode/` 由用户明确要求保留，当前仅作为被 `.gitignore` 排除的本地工具目录；它不属于产品源码、权威计划、验收证据或正式交付包，其存在本身不是 P0-Prep 阻塞项。
- Docker 客户端 29.6.1 已安装，但 2026-08-13 复核时本地 Docker daemon 未运行；这不阻塞 P0-Prep，进入 P0 前必须重新验证并启动可用的本地容器环境。
- 2026-08-13 已运行 `python3 scripts/validate_p0_prep.py`：仓库/归属 5/5、协议/引用 12/12、阶段基线 5/5、P0 可复现性 8/8、包卫生 7/7，合计 37/37 PASS；原始结果位于 `evidence/prep/`。
- 当前没有 Elasticsearch/OpenSearch、RocketMQ、Netty、Vue3 或若依代码/配置证据。

## 2. 已批准的产品决策

- 产品：面向 3～30 人视频创作团队的协同与交付平台。
- MVP 资源隔离：按 `Team/Project` 校验，不做首期复杂企业多租户。
- MVP 首期部署：本地 Docker Compose。
- MVP 先使用 Fake/Stub AI Provider，真实 Provider 后置。
- MVP 先做文本与时间码评论，区域批注后置。
- MVP 不做浏览器剪辑器、支付、合同、复杂 CRM/ERP、自研模型或推理集群。

## 3. 已批准的技术决策（均为计划，尚未实现）

- 仓库采用模式 A（源码单仓库）：代码、docs/、deploy/ 与 evidence/ 同库。
- 开发派发以 `docs/05-engineering/tasks/**/*.json` 结构化任务包为准；Task/Evidence/Test 编号体系见 task-capsule-spec.md。
- 服务归属定稿（ADR-008）：任务/评论归 project-service，审核/授权/交付归 asset-workflow-service，AI Worker 不拥有业务表。
- **JDK 17 是唯一基线**（本机环境）；配套 Spring Boot 3.4.x、Maven 版本均与 Java 17 匹配。
- 阶段技术基线：P0～M4-A 使用本地 StoragePort（不引入 MinIO）；M4-B 首次引入 MinIO；Testcontainers 从每个真实基础设施首次引入时开始使用，M12-B 只做体系强化。
- Spring Boot 3.x（P0 固定具体版本，当前批准 3.4.5）、Maven、Spring MVC、Spring Security 6、JWT、BCrypt、RBAC。
- MyBatis-Plus + XML、Flyway、PostgreSQL。
- MVP 唯一事务主库为 PostgreSQL；不做 MySQL/PostgreSQL 双写或跨库事务。
- M4-B 首次引入 MinIO；M5～M8 引入 Redis、RabbitMQ 和可替换 AI Provider。
- M9～M12（engineering-lab）引入 DDD、Outbox/Kafka、压测和 JVM 实验。
- M13～M15（engineering-lab）拆 Gateway、Identity、Project、Asset-Workflow；`identity-service` 使用 MySQL 是工程实验目标。
- M16 使用 Docker、Helm、kind/minikube 和 Kubernetes；M16-G 做受限 Istio 灰度/mTLS 实验。
- 可观测性采用 Actuator/Micrometer、OpenTelemetry、Prometheus/Grafana；日志采用 Fluent Bit、Elasticsearch、Kibana。

## 4. 当前目标阶段

```text
当前：P0-Prep 已通过（FF-PP-001=DONE），业务开发进度仍为 0
下一步：取得用户对初始化 Git 与执行 P0 的明确授权，并启动可用的 Docker daemon
随后：环境预检通过后将 FF-P0-001 标记 READY_FOR_DISPATCH；派发后首个受审批动作是初始化 Git 并建立基线，完成后再实施工程基座
之后：产品主线 P0→M8 → 工程实验 M9→M17（按选择执行）
```

## 5. 阶段状态

| 阶段 | 目标 | 当前状态 | 完成判定 |
|---|---|---|---|
| P0-Prep | 执行基线收敛（仓库角色/归属/编号/任务包格式/契约/证据/包卫生） | **PASSED（FF-PP-001=DONE）** | 2026-08-13 五类机器门禁 37/37 PASS，原始证据已登记 |
| P0 | Java/Spring Boot 基座、PostgreSQL、迁移、健康检查 | 未开始（禁止派发） | 双授权与环境预检后派发；任务内建立 Git 基线，并有源码、构建、测试、启动和证据 |
| M1～M4 | 身份、项目、任务、素材核心闭环 | M01 契约已收敛为 CONTRACT_READY；等待 P0 完成，尚不可派发 | 业务 API、权限、事务、版本和 E2E |
| M5～M8 | Redis、MinIO、RabbitMQ、AI suggestion、审核交付 | 未开始 | 基础设施集成和故障证据 |
| M9～M12 | DDD、Outbox/Kafka、测试、压测、JVM（lab） | 未开始 | 原始测试/性能/采样/复盘 |
| M13～M15 | 微服务和 Spring Cloud 治理（lab） | 未开始 | 数据所有权、契约、超时熔断、Trace |
| M16 | Docker/Helm/Kubernetes（lab） | 未开始 | 部署、探针、资源、发布、回滚 |
| M16-G | Istio 灰度、mTLS、授权和故障注入（lab） | 未开始 | YAML、命令、流量和安全证据 |
| M17 | 交付和求职材料 | 未开始 | 证据索引、演示稿和诚实简历口径 |

## 6. 阻塞项与风险

- P0-Prep 已通过并有原始证据，不再是阻塞项。
- P0 仍不得派发：Git 初始化与 P0 执行尚未取得用户明确授权，且 Docker daemon 当前未运行；`FF-P0-001` 保持 `NOT_READY`。
- M01 仍不得派发：它依赖 P0 验收通过；当前仅为 `CONTRACT_READY`。
- P0-Prep 发生在 Git 初始化之前，其原始证据用时间、命令、路径和内容摘要锚定并保持 pre-Git 不可变；首个 Git commit 由 P0 Receipt/Evidence 记录为后继锚点，不回写或伪装成 PP 证据所属 commit。此例外只适用于 P0-Prep。
- Git 初始化不是 P0-Prep 的通过条件。它是 P0 开始动作，并且必须在 P0-Prep 通过后取得用户明确授权；授权前保持当前非 Git 工作区。
- `.zcode/` 允许保留且已由忽略规则覆盖；P0-Prep 验证忽略与打包规则，Git 基线建立后再验证它未被跟踪、未进入正式归档；任何阶段都不把它当作权威事实源，也不要求删除本地目录。
- JDK 17 已满足（本机 17.0.18）；Spring Boot 3.4.x 与 Java 17 兼容。Docker daemon 当前未运行，服务可用性须在 P0 开始时重新验证。
- 尚未锁定前端范围、真实 Provider、媒体规格和首个真实试用对象；这些不阻塞 P0。
- 外部服务、真实密钥、客户数据和旧项目配置永远不进入本项目。

## 7. 下一步入口

1. 请求并取得用户对 Git 初始化与执行 P0 的明确授权。
2. 启动 Docker daemon，并在 P0 开始前重新验证 Java、Maven、Git、Docker 与 Compose。
3. 双授权和环境预检满足后，由调度器将 `FF-P0-001` 标记 `READY_FOR_DISPATCH` 并生成受限 Capability Grant。
4. 派发 P0；任务首个受审批动作是建立可追溯 Git 基线，并由 P0 Receipt/Evidence 记录首个 commit 作为 PP 当前态的后继锚点，随后实施工程基座；不得提前实现 M01 业务。
5. P0 验收通过且 M01 Ready Gate 复核通过后，才把 `FF-M01-001` 标记 READY_FOR_DISPATCH。
6. 每个阶段完成后，工具按 `evidence-index.md` 回写证据。

## 8. 更新规则

每完成一个阶段，必须记录：

- 实际修改的代码和文档；
- Git commit（P0-Prep 保持 pre-Git 例外；首个后继 commit 由 P0 证据记录）；
- 精确命令与原始输出；
- 测试、压测、故障或部署证据；
- 未完成项、限制和回滚方式；
- 下一阶段进入条件。

没有运行证据的内容必须标记为“计划/未验证”。
