# 交付证据索引

> 状态：**P0 已完成；M01 已完成开发与验收，四条证据已登记**。P0-Prep 已于 2026-08-13 通过五类机器门禁（37/37 项 PASS）；P0 六条证据均已登记。证据编号必须与任务包 JSON 中声明的 `evidenceId` 一致（见 `docs/05-engineering/schemas/task-capsule.schema.json`）。

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
| EV-FF-M01-001-01 | FF-M01-001 | M01 | Spring Security/RBAC/幂等/Token | `mvn -B clean verify`；`evidence/m01/mvn-verify.txt` | PASS | 权限、幂等与会话安全如何落地 |
| EV-FF-M01-001-02 | FF-M01-001 | M01 | 认证与资源授权流 | `sh scripts/m01/auth-flows.sh`；`evidence/m01/auth-flows.txt` | PASS（25/25） | 401/403/404、OWNER 不变量与 requestId |
| EV-FF-M01-001-03 | FF-M01-001 | M01 | 身份库迁移 | `sh scripts/m01/check-migration.sh`；`evidence/m01/migration.txt` | PASS（7/7） | forward-only 与模块表边界 |
| EV-FF-M01-001-04 | FF-M01-001 | M01 | OpenAPI 契约防漂移 | `sh scripts/m01/check-openapi-diff.sh`；`evidence/m01/openapi-diff.txt` | PASS | 契约优先如何落实 |
| EV-FF-M02-001-01 | FF-M02-001 | M2 | 事务与状态机 | Brief 切换回滚测试 | 未开始 | 如何避免并发覆盖 |
| EV-FF-M03-001-01 | FF-M03-001 | M3 | 乐观锁 | 双旧 version 并发用例 | 未开始 | 409 冲突语义 |
| EV-FF-M04A-001-01 | FF-M04A-001 | M4-A | 本地存储与补偿 | 上传/补偿测试 | 未开始 | DB 与文件一致性 |
| EV-FF-M04B-001-01 | FF-M04B-001 | M4-B | MinIO 预签名 | 权限/过期测试 | 未开始 | 对象存储授权 |
| EV-FF-M05-001-01 | FF-M05-001 | M5 | Redis 缓存/幂等/限流 | 命中/429/并发幂等 | 未开始 | Redis 不是事实源 |
| EV-FF-M06-001-01 | FF-M06-001 | M6 | RabbitMQ 可靠任务 | Confirm/ACK/DLQ/重放 | 未开始 | RabbitMQ 与 Kafka 区别 |
| EV-FF-M07-001-01 | FF-M07-001 | M7 | AI Provider Adapter | FakeProvider 测试 | 未开始 | AI 如何避免污染业务事实 |
| EV-FF-M08-001-01 | FF-M08-001 | M8 | 审核与交付 E2E | 完整闭环演示 | 未开始 | 业务闭环 |
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

已创建任务实例（当前为 PP、P0、M01）的证据索引与任务包 JSON `evidence` 字段必须双向一一对应；任一缺失或多余均视为该任务包未完成。M02 及以后尚无任务实例的行只是规划期 Evidence ID 预留，不代表任务可派发或证据已产生；创建相应任务 JSON 时必须重新校验并同步。

## 4. P0-Prep 的 Git 前置例外

P0-Prep 在 Git 和控制面建立之前执行。其证据先记录验证时间、环境、精确验证命令、原始输出路径、结果与内容摘要；不得虚构 commit、Capability Grant、Agent Receipt、exact changedPaths 或 budget 记录。一次性例外的事实边界、已知修复范围、无法重建项和不可复用规则见 [`evidence/prep/p0-prep-bootstrap-audit.md`](../../evidence/prep/p0-prep-bootstrap-audit.md)。37/37 PASS 证明修复后当前基线通过门禁，不证明历史执行过程符合后来才建立的控制面协议。

P0-Prep 全部验证通过后，须分别取得用户对 Git 初始化与执行 P0 的明确授权并完成环境预检；调度器把 P0 标记为可派发并签发受限 Grant 后，P0 的首个受审批动作才建立 Git 基线。首个 commit 由 P0 Receipt/Evidence 记录为 PP 当前态之后的后继锚点；PP 五份 pre-Git 原始证据不重写、不归因到该 commit。该 commit 不能倒推 bootstrap exact diff、命令或 budget。此例外仅限 `FF-PP-001`，不可复制；P0 及后续任务必须具有事前 Grant、合法 Receipt 和完整正常完成证据。

`.zcode/` 允许留在本地工作区且由 `.gitignore` 排除。PP 包卫生证据先验证忽略与正式归档排除规则、非权威边界；Git 基线建立后再验证它未被跟踪且未进入归档。任何检查都不验证或要求本地目录被删除。
