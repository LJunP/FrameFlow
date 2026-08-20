# 项目状态

> 本文只记录当前可验证事实、已批准决策和明确的下一步，不把设计目标写成已实现能力。

## 1. 当前已验证事实

- `main` 已包含 **P0 工程基座**、**M01 身份/授权功能**与 **M01-Hardening**，`FF-P0-001`、`FF-M01-001`、`FF-M01H-001` 均为 `DONE`。M01-H 的构建、认证、迁移、OpenAPI 与治理 Evidence 全部锚定实现提交 `4139d94`，Receipt 为 `COMPLETED`。
- 已重建文档事实源：README、三书、治理、API、工程、测试、运维、证据索引和 ADR-001～012。
- 当前已有 `pom.xml`、`src/`、Flyway V1/V2/V3 迁移、Docker Compose、探针测试、P0/M01/M01-H 验收脚本；V3 注释迁移按用户授权独立提交为 `2aa295d`，只增加数据库注释，不改结构或数据。尚无 Dockerfile、Kubernetes 清单、压测结果、JVM 采样或 Istio 实验记录。
- Git 已初始化为 `main` 基线并创建独立 P0 分支；P0 实现提交和后续证据提交由本任务记录。
- `.zcode/` 由用户明确要求保留，当前仅作为被 `.gitignore` 排除的本地工具目录；它不属于产品源码、权威计划、验收证据或正式交付包，其存在本身不是 P0-Prep 阻塞项。
- Docker Engine 29.6.1、Compose v5.3.0 已在 P0 运行验证；Testcontainers 使用本地 Docker socket 的 API 1.44 兼容配置。
- 2026-08-13 已运行 `python3 scripts/validate_p0_prep.py`：仓库/归属 5/5、协议/引用 12/12、阶段基线 5/5、P0 可复现性 8/8、包卫生 7/7，合计 37/37 PASS；原始结果位于 `evidence/prep/`。
- 当前没有 Elasticsearch/OpenSearch、RocketMQ、Netty 或若依代码/配置证据。本仓库 `frameflow-web/` 前端代码尚未开始；`FF-M01F-001` 的 M01-H 前置已满足，但在精确版本、授权与派发条件锁定前仍保持 `NOT_READY`。

## 2. 已批准的产品决策

- 产品：面向 3～30 人视频创作团队的协同与交付平台。
- MVP 资源隔离：按 `Team/Project` 校验，不做首期复杂企业多租户。
- MVP 首期部署：本地 Docker Compose。
- MVP 先使用 Fake/Stub AI Provider，真实 Provider 后置。
- MVP 先做文本与时间码评论，区域批注后置。
- MVP 不做浏览器剪辑器、支付、合同、复杂 CRM/ERP、自研模型或推理集群。
- 前端范围已锁定（ADR-012）：React + TypeScript + Next.js App Router/BFF + Tailwind CSS + shadcn/ui + TanStack Query + openapi-typescript；代码放在本仓库 `frameflow-web/`，不单独建仓。具体版本由每个任务在派发时选受支持稳定版并精确锁定。

## 3. 已批准的技术基线与演进决策

其中 P0/M01 对应能力已有实现与证据；M02 以后以及 engineering-lab 内容仍是计划，除非后续阶段 Evidence 另行证明。

- 仓库采用模式 A（源码单仓库）：代码、docs/、deploy/ 与 evidence/ 同库。
- 开发派发以 `docs/05-engineering/tasks/**/*.json` 结构化任务包为准；Task/Evidence/Test 编号体系见 task-capsule-spec.md。
- 服务归属定稿（ADR-008）：任务/评论归 project-service，审核/授权/交付归 asset-workflow-service，AI Worker 不拥有业务表。
- **JDK 17 是唯一基线**（本机环境）；配套 Spring Boot 3.4.x、Maven 版本均与 Java 17 匹配。
- 阶段技术基线：P0～M4-A 使用本地 StoragePort（不引入 MinIO）；M4-B 首次引入 MinIO；Testcontainers 从每个真实基础设施首次引入时开始使用，M12-B 只做体系强化。
- Spring Boot 3.x（P0 固定具体版本，当前批准 3.4.5）、Maven、Spring MVC、Spring Security 6、JWT、BCrypt、RBAC。
- MyBatis-Plus + XML、Flyway、PostgreSQL。
- MVP 唯一事务主库为 PostgreSQL；不做 MySQL/PostgreSQL 双写或跨库事务。
- M4-B 首次引入 MinIO；M6～M8 引入 RabbitMQ、可替换 AI Provider 与审核/交付闭环。M05 Redis 改为可选 hardening/engineering-lab，不是 MVP 或 M08/M08-F 的前置。
- M9～M12（engineering-lab）引入 DDD、Outbox/Kafka、压测和 JVM 实验。
- M13～M15（engineering-lab）拆 Gateway、Identity、Project、Asset-Workflow；`identity-service` 使用 MySQL 是工程实验目标。
- M16 使用 Docker、Helm、kind/minikube 和 Kubernetes；M16-G 做受限 Istio 灰度/mTLS 实验。
- 可观测性采用 Actuator/Micrometer、OpenTelemetry、Prometheus/Grafana；日志采用 Fluent Bit、Elasticsearch、Kibana。
- 前端技术选型已由本次用户授权批准（ADR-012）；阶段只使用 `M01-F / FF-M01F-001`、`M04-F / FF-M04F-001`、`M08-F / FF-M08F-001`，且全部属于 `product-mainline`。
- Next.js 作为同源 BFF；Refresh Token 只能由 BFF 写入 `Secure`/`HttpOnly`/`SameSite` Cookie，禁止 localStorage/sessionStorage/IndexedDB。每次 HTTP attempt 的 `X-Request-Id` 由后端生成；需串联多个请求时使用另行校验的 `X-Correlation-Id`。

## 4. 当前目标阶段

```text
当前：FF-PP-001 / FF-P0-001 / FF-M01-001 / FF-M01H-001 均为 DONE
下一主线：M02 Contract Gate 草案（FF-M02-001=DRAFT），先解决 UNKNOWN 并取得用户批准
之后：M02 Contract Gate 获批准后才能派发 M02 实现；M08 是后端门禁，M08-F 是产品 MVP 门禁，真实用户验证只在 M08-F 之后开始
```

## 5. 阶段状态

| 阶段 | 目标 | 当前状态 | 完成判定 |
|---|---|---|---|
| P0-Prep | 执行基线收敛（仓库角色/归属/编号/任务包格式/契约/证据/包卫生） | **PASSED（FF-PP-001=DONE）** | 2026-08-13 五类机器门禁 37/37 PASS，原始证据已登记 |
| P0 | Java/Spring Boot 基座、PostgreSQL、迁移、健康检查 | **DONE** | 六条验收证据、Receipt、卫生检查和状态回写全部 PASS |
| M01-H | M01 验收、安全与架构加固 | **DONE** | 五条 Evidence 锚定 `4139d94`，Receipt=`COMPLETED` |
| M02 Contract Gate | 项目/Brief 数据、API、权限、测试与 Evidence 决策 | **DRAFT，尚未批准** | UNKNOWN 全部解决、用户批准，权威契约另行受控更新 |
| M01-F / M04-F / M08-F | 本仓库 `frameflow-web/` | DRAFT/NOT_READY，未实现 | 分别依赖 M01H、M02/M03/M04A、M07/M08；M08-F 是产品 MVP Gate |
| M02～M08 | 项目到审核/交付的后端闭环 | M02 尚在 Contract Gate，未实现 | M08 是 backend gate；M05 Redis 可选且不阻塞 |
| M05 | Redis 缓存/限流/幂等加速实验 | 可选 engineering-lab，未开始 | PostgreSQL 仍为事实源；不阻塞 M08/M08-F |
| M9～M12 | DDD、Outbox/Kafka、测试、压测、JVM（lab） | 未开始 | 原始测试/性能/采样/复盘 |
| M13～M15 | 微服务和 Spring Cloud 治理（lab） | 未开始 | 数据所有权、契约、超时熔断、Trace |
| M16 | Docker/Helm/Kubernetes（lab） | 未开始 | 部署、探针、资源、发布、回滚 |
| M16-G | Istio 灰度、mTLS、授权和故障注入（lab） | 未开始 | YAML、命令、流量和安全证据 |
| M17 | 交付和求职材料 | 未开始 | 证据索引、演示稿和诚实简历口径 |

## 6. 阻塞项与风险

- P0-Prep 已通过并有原始证据，不再是阻塞项。
- P0 已完成：Git 初始化、P0 执行授权、环境预检、六条证据和 Receipt 均通过。
- M01 功能与 M01-Hardening 均已完成；旧 M01 Evidence 已安全脱敏并标记失效，M01-H 替代 Evidence 已闭环。旧 Token 是否已从 Git 历史撤销仍为 UNKNOWN，历史清理需另行授权。
- P0-Prep 发生在 Git 初始化之前，其原始证据用时间、命令、路径和内容摘要锚定并保持 pre-Git 不可变；首个 Git commit 由 P0 Receipt/Evidence 记录为后继锚点，不回写或伪装成 PP 证据所属 commit。此例外只适用于 P0-Prep。
- Git 初始化不是 P0-Prep 的通过条件；它已在 P0-Prep 通过并取得用户授权后作为 P0 动作完成。当前仓库已经是 Git 仓库，这条只说明历史授权边界，不是当前阻塞项。
- `.zcode/` 允许保留且已由忽略规则覆盖；P0-Prep 验证忽略与打包规则，Git 基线建立后再验证它未被跟踪、未进入正式归档；任何阶段都不把它当作权威事实源，也不要求删除本地目录。
- JDK 17 已满足（本机 17.0.18）；Spring Boot 3.4.x 与 Java 17 兼容。Docker Engine 29.6.1 与 Compose v5.3.0 已在 P0 验收中验证；数据库容器按需启动并由脚本恢复。
- 前端技术方向已批准，但各 Task 的精确版本必须在派发时锁定。真实 Provider、媒体规格与首个真实试用对象仍为 UNKNOWN；用户验证不早于 M08-F Gate。
- 外部服务、真实密钥、客户数据和旧项目配置永远不进入本项目。

## 7. 下一步入口

1. 完善 `FF-M02-001` Contract Gate 的 UNKNOWN/决策输入并逐项取得用户批准；本阶段不写 M02 业务代码，不修改 `docs/04-api/openapi/frameflow-v1.yaml`。
2. Contract Gate 批准后，另立受控契约更新动作，再决定是否将 M02 实现任务从 `NOT_READY` 提升。
3. 若选择并行启动 M01-F，先锁定派发日受支持的 Node.js、包管理器与核心依赖精确版本，再签发受限 Grant；代码只放在本仓库 `frameflow-web/`。
4. 旧 Token 历史清理与撤销另立安全任务，不与 M02 或前端开发混做。

## 8. 更新规则

每完成一个阶段，必须记录：

- 实际修改的代码和文档；
- Git commit（P0-Prep 保持 pre-Git 例外；首个后继 commit 由 P0 证据记录）；
- 精确命令与原始输出；
- 测试、压测、故障或部署证据；
- 未完成项、限制和回滚方式；
- 下一阶段进入条件。

没有运行证据的内容必须标记为“计划/未验证”。
