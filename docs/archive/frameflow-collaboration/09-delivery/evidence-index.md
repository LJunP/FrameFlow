# 交付证据索引

> 状态：**P0、M01 功能与 M01-H 已完成；M01 旧证据因安全与治理问题标记 SUPERSEDED / SECURITY-REDACTED，M01-H 五条替代证据全部锚定实现提交 `4139d94` 并通过**。P0-Prep 已于 2026-08-13 通过五类机器门禁（37/37 项 PASS）；M01-H Receipt=`COMPLETED`、Task=`DONE`。证据编号必须与任务包 JSON 中声明的 `evidenceId` 一致（见 `docs/05-engineering/schemas/task-capsule.schema.json`）。

## 1. 证据总表

| Evidence ID | Task ID | 阶段 | 目标能力 | 验证命令/原始结果 | 状态 | 面试主题 |
|---|---|---|---|---|---|---|
| EV-FF-PP-001-01 | FF-PP-001 | P0-Prep | 仓库模式与服务归属一致性 | `python3 scripts/validate_p0_prep.py`；`evidence/prep/consistency.txt` | PASS（5/5） | 事实源如何收敛 |
| EV-FF-PP-001-02 | FF-PP-001 | P0-Prep | Schema、编号、依赖和引用完整性 | `python3 scripts/validate_p0_prep.py`；`evidence/prep/schema-validate.txt` | PASS（12/12） | 任务为何可机器派发 |
| EV-FF-PP-001-03 | FF-PP-001 | P0-Prep | 阶段技术基线一致性 | `python3 scripts/validate_p0_prep.py`；`evidence/prep/stage-baseline.txt` | PASS（5/5） | 如何防止计划漂移 |
| EV-FF-PP-001-04 | FF-PP-001 | P0-Prep | P0 任务可复现性 | `python3 scripts/validate_p0_prep.py`；`evidence/prep/p0-task-review.txt` | PASS（8/8） | 如何定义可复现任务 |
| EV-FF-PP-001-05 | FF-PP-001 | P0-Prep | 包卫生与本地工具隔离 | `python3 scripts/validate_p0_prep.py`；`evidence/prep/hygiene.txt` | PASS（7/7） | 本地工具与交付物如何隔离 |
| EV-FF-P0-001-01 | FF-P0-001 | P0 | Spring Boot/PostgreSQL 基座 | `sh scripts/p0/check-build.sh`；`evidence/p0/mvn-verify.txt` | PASS | 为什么先做单体 |
| EV-FF-P0-001-02 | FF-P0-001 | P0 | health 存活语义 | `sh scripts/p0/check-health.sh`；`evidence/p0/health.txt` | PASS | 存活与就绪为何分离 |
| EV-FF-P0-001-03 | FF-P0-001 | P0 | readiness 依赖失败语义 | `sh scripts/p0/check-readiness-db-down.sh`；`evidence/p0/readiness-db-down.txt` | PASS | 依赖失败如何退流量 |
| EV-FF-P0-001-04 | FF-P0-001 | P0 | readiness 恢复语义 | `sh scripts/p0/check-readiness-db-up.sh`；`evidence/p0/readiness-db-up.txt` | PASS | 依赖恢复判断 |
| EV-FF-P0-001-05 | FF-P0-001 | P0 | Flyway 可重复迁移 | `sh scripts/p0/check-flyway.sh`；`evidence/p0/flyway-migrate.txt` | PASS | 数据库版本如何演进 |
| EV-FF-P0-001-06 | FF-P0-001 | P0 | Git 与敏感文件卫生 | `sh scripts/p0/check-repo-hygiene.sh`；`evidence/p0/repo-hygiene.txt` | PASS（针对实现/运行证据提交） | 如何保持可交付基线 |
| EV-FF-M01-001-01 | FF-M01-001 | M01 | Spring Security/RBAC/幂等/Token | 历史命令与脱敏副本：`evidence/m01/mvn-verify.txt` | SUPERSEDED / SECURITY-REDACTED | 权限、幂等与会话安全如何落地 |
| EV-FF-M01-001-02 | FF-M01-001 | M01 | 认证与资源授权流 | 历史脱敏副本：`evidence/m01/auth-flows.txt` | SUPERSEDED / SECURITY-REDACTED | 401/403/404、OWNER 不变量与 requestId |
| EV-FF-M01-001-03 | FF-M01-001 | M01 | 身份库迁移 | 历史脱敏副本：`evidence/m01/migration.txt` | SUPERSEDED / SECURITY-REDACTED | forward-only 与模块表边界 |
| EV-FF-M01-001-04 | FF-M01-001 | M01 | OpenAPI 契约防漂移 | 历史脱敏副本：`evidence/m01/openapi-diff.txt` | SUPERSEDED / SECURITY-REDACTED | 契约优先如何落实 |
| EV-FF-M01H-001-01 | FF-M01H-001 | M01-H | 全量构建与安全/架构回归 | `sh scripts/m01h/check-build.sh`；`evidence/m01h/mvn-verify.txt` | PASS（`4139d94`） | 加固后如何建立可复现构建证据 |
| EV-FF-M01H-001-02 | FF-M01H-001 | M01-H | Evidence 与治理一致性 | `sh scripts/m01h/check-hardening.sh`；`evidence/m01h/hardening-checks.txt` | PASS（`4139d94`） | 如何机械防止秘密、路径和治理失真 |
| EV-FF-M01H-001-03 | FF-M01H-001 | M01-H | 认证与资源授权流复验 | `FRAMEFLOW_M01_EVIDENCE_DIR=evidence/m01h sh scripts/m01/auth-flows.sh`；`evidence/m01h/auth-flows.txt` | PASS（`4139d94`） | 如何从生成源阻断 Token 落盘 |
| EV-FF-M01H-001-04 | FF-M01H-001 | M01-H | V1 至当前版本动态迁移 | `FRAMEFLOW_M01_EVIDENCE_DIR=evidence/m01h sh scripts/m01/check-migration.sh`；`evidence/m01h/migration.txt` | PASS（`4139d94`） | forward-only 迁移如何随版本演进 |
| EV-FF-M01H-001-05 | FF-M01H-001 | M01-H | OpenAPI 功能字段防漂移 | `FRAMEFLOW_M01_EVIDENCE_DIR=evidence/m01h sh scripts/m01/check-openapi-diff.sh`；`evidence/m01h/openapi-diff.txt` | PASS（`4139d94`） | 契约比较如何覆盖安全与响应语义 |
| EV-FF-M01F-001-01 | FF-M01F-001 | M01-F | 前端构建与契约生成 | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m01f sh frameflow-web/scripts/check-verify.sh`；`evidence/m01f/build.txt` | 未开始 | 前端基线如何保持可复现 |
| EV-FF-M01F-001-02 | FF-M01F-001 | M01-F | BFF 与浏览器安全 | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m01f sh frameflow-web/scripts/check-security.sh`；`evidence/m01f/security-tests.txt` | 未开始 | Cookie、轮换、CSRF 与存储安全 |
| EV-FF-M01F-001-03 | FF-M01F-001 | M01-F | 认证与团队前端 E2E | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m01f sh frameflow-web/scripts/check-e2e.sh`；`evidence/m01f/e2e.txt` | 未开始 | 前后端错误语义如何一致 |
| EV-FF-M02-001-01 | FF-M02-001 | M02 | Contract Gate | `sh scripts/m02/check-contract-gate.sh`；`evidence/m02/contract-gate.txt` | 未开始 | 如何先收敛 UNKNOWN 再派发实现 |
| EV-FF-M02-002-01 | FF-M02-002 | M02 | 项目/客户/成员基座 | `sh scripts/m02/check-project-foundation.sh`；`evidence/m02/project-foundation.txt` | 未开始 | 权限、事务、幂等与并发错误语义 |
| EV-FF-M02-002-02 | FF-M02-002 | M02 | 项目域迁移 | `sh scripts/m02/check-migration.sh`；`evidence/m02/migration.txt` | 未开始 | V1 至 V4 forward-only 与 no-op 复验 |
| EV-FF-M02-003-01 | FF-M02-003 | M02 | Brief 版本与状态机 | `sh scripts/m02/check-brief-workflow.sh`；`evidence/m02/brief-workflow.txt` | 未开始 | 单一 current、回滚、并发 409 |
| EV-FF-M02-004-01 | FF-M02-004 | M02 | M02 全量构建 | `sh scripts/m02/check-build.sh`；`evidence/m02/mvn-verify.txt` | 未开始 | 项目域如何完成测试收口 |
| EV-FF-M02-004-02 | FF-M02-004 | M02 | M02 OpenAPI 契约 | `sh scripts/m02/check-api-contract.sh`；`evidence/m02/api-contract.txt` | 未开始 | 运行时契约如何防漂移 |
| EV-FF-M02-004-03 | FF-M02-004 | M02 | M02 业务闭环 E2E | `sh scripts/m02/check-e2e.sh`；`evidence/m02/e2e.txt` | 未开始 | 项目、成员、Brief 与审计闭环 |
| EV-FF-M03-001-01 | FF-M03-001 | M3 | 乐观锁 | 双旧 version 并发用例 | 未开始 | 409 冲突语义 |
| EV-FF-M04A-001-01 | FF-M04A-001 | M4-A | 本地存储与补偿 | 上传/补偿测试 | 未开始 | DB 与文件一致性 |
| EV-FF-M04F-001-01 | FF-M04F-001 | M04-F | 核心业务前端联合门禁 | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m04f sh frameflow-web/scripts/check-verify.sh`；`evidence/m04f/verify.txt` | 未开始 | 类型、构建与契约如何联合门禁 |
| EV-FF-M04F-001-02 | FF-M04F-001 | M04-F | 项目/任务/素材前端 E2E | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m04f sh frameflow-web/scripts/check-e2e.sh`；`evidence/m04f/e2e.txt` | 未开始 | 跨模块界面如何端到端验收 |
| EV-FF-M04B-001-01 | FF-M04B-001 | M4-B | MinIO 预签名 | 权限/过期测试 | 未开始 | 对象存储授权 |
| EV-FF-M05-001-01 | FF-M05-001 | M5 | Redis 缓存/幂等/限流 | 命中/429/并发幂等 | 未开始 | Redis 不是事实源 |
| EV-FF-M06-001-01 | FF-M06-001 | M6 | RabbitMQ 可靠任务 | Confirm/ACK/DLQ/重放 | 未开始 | RabbitMQ 与 Kafka 区别 |
| EV-FF-M07-001-01 | FF-M07-001 | M7 | AI Provider Adapter | FakeProvider 测试 | 未开始 | AI 如何避免污染业务事实 |
| EV-FF-M08-001-01 | FF-M08-001 | M8 | 审核与交付 E2E | 完整闭环演示 | 未开始 | 业务闭环 |
| EV-FF-M08F-001-01 | FF-M08F-001 | M08-F | 产品前端联合门禁 | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m08f sh frameflow-web/scripts/check-verify.sh`；`evidence/m08f/verify.txt` | 未开始 | 可访问性与契约漂移如何入门禁 |
| EV-FF-M08F-001-02 | FF-M08F-001 | M08-F | 产品 MVP 全链路 E2E | `FRAMEFLOW_WEB_EVIDENCE_DIR=evidence/m08f sh frameflow-web/scripts/check-e2e.sh`；`evidence/m08f/product-e2e.txt` | 未开始 | 何时可进入真实用户验证 |
| EV-FF-M09-001-01 | FF-M09-001 | M9 | DDD 领域建模 | 纯领域测试 | 未开始 | 聚合边界 |
| EV-FF-M10-001-01 | FF-M10-001 | M10 | PostgreSQL 深化 | EXPLAIN 前后对比 | 未开始 | JSONB/GIN 取舍 |
| EV-FF-M11-001-01 | FF-M11-001 | M11 | Outbox/Kafka | 事件/重复消费/DLT | 未开始 | 如何处理双写 |
| EV-FF-M12A-001-01 | FF-M12A-001 | M12-A | 高风险业务测试 | 关键规则测试报告 | 未开始 | 测试行为覆盖 |
| EV-FF-M12B-001-01 | FF-M12B-001 | M12-B | Testcontainers 强化 | 四类基础设施集成测试 | 未开始 | 集成测试价值 |
| EV-FF-M12C-001-01 | FF-M12C-001 | M12-C | 压测基线 | k6 原始数据 | 未开始 | 性能定位顺序 |
| EV-FF-M12D-001-01 | FF-M12D-001 | M12-D | JVM 故障实验 | JFR/jstack/GC 复盘 | 未开始 | 线程池/内存定位 |
| EV-FF-M13-001-01 | FF-M13-001 | M13 | 微服务拆分 | 服务图/迁移/回滚 | 未开始 | 为什么拆服务 |
| EV-FF-M14-001-01 | FF-M14-001 | M14 | 服务治理 | 超时/熔断/降级实验 | 未开始 | 避免重试风暴 |
| EV-FF-M15-001-01 | FF-M15-001 | M15 | Trace/指标/日志 | OTel/Grafana/ELK | 未开始 | 跨服务故障定位 |
| EV-FF-M16-001-01 | FF-M16-001 | M16 | Kubernetes | kubectl/helm 输出 | 未开始 | CrashLoop/OOM 排查 |
| EV-FF-M16G-001-01 | FF-M16G-001 | M16-G | Istio | 灰度/mTLS/回滚 | 未开始 | Mesh 解决什么问题 |
| EV-FF-M17-001-01 | FF-M17-001 | M17 | 求职交付 | README/演示/证据 | 未开始 | 事实边界 |

## 2. 证据格式（每条必须包含）

```text
Evidence ID
对应 Task ID 与阶段
Git commit（由调度工具记录；P0-Prep 原始证据按第 4 节保持 pre-Git，首个后继 commit 由 P0 证据记录）
环境（JDK/OS/容器/数据量/并发）
前置条件
精确命令
原始输出或报告路径
预期与实际结果
失败与修复
限制条件
对应招聘能力和面试问题
```

## 3. 追踪关系

```text
Requirement ID（REQ-*）→ Task ID（FF-*）→ Test ID（TEST-FF-*）→ Evidence ID（EV-FF-*）
```

已创建任务实例的证据索引与任务包 JSON `evidence` 字段必须双向一一对应；任一缺失或多余均视为该任务包未完成。M01 四份旧文本证据仅做安全脱敏并保留其中原 `subject commit` 作为历史记录，不把脱敏后的文件冒充该 commit 的原始 bytes，也不再作为当前验收依据；工作树脱敏不等于历史 Token 已撤销，也不等于 Git 历史已清理。M01-H 五条正式证据均记录 base commit、工作树状态与 formal subject commit `4139d94e71440b565bd1424c035bd4ce5bdd0086`；最终 Evidence/状态提交是其后继，不替换实现锚点。尚无任务实例的规划行不代表任务可派发或证据已产生；创建相应任务 JSON 时必须重新校验并同步。

## 4. P0-Prep 的 Git 前置例外

P0-Prep 在 Git 和控制面建立之前执行。其证据先记录验证时间、环境、精确验证命令、原始输出路径、结果与内容摘要；不得虚构 commit、Capability Grant、Agent Receipt、exact changedPaths 或 budget 记录。一次性例外的事实边界、已知修复范围、无法重建项和不可复用规则见 [`evidence/prep/p0-prep-bootstrap-audit.md`](../../evidence/prep/p0-prep-bootstrap-audit.md)。37/37 PASS 证明修复后当前基线通过门禁，不证明历史执行过程符合后来才建立的控制面协议。

P0-Prep 全部验证通过后，须分别取得用户对 Git 初始化与执行 P0 的明确授权并完成环境预检；调度器把 P0 标记为可派发并签发受限 Grant 后，P0 的首个受审批动作才建立 Git 基线。首个 commit 由 P0 Receipt/Evidence 记录为 PP 当前态之后的后继锚点；PP 五份 pre-Git 原始证据不重写、不归因到该 commit。该 commit 不能倒推 bootstrap exact diff、命令或 budget。此例外仅限 `FF-PP-001`，不可复制；P0 及后续任务必须具有事前 Grant、合法 Receipt 和完整正常完成证据。

`.zcode/` 允许留在本地工作区且由 `.gitignore` 排除。PP 包卫生证据先验证忽略与正式归档排除规则、非权威边界；Git 基线建立后再验证它未被跟踪且未进入归档。任何检查都不验证或要求本地目录被删除。
