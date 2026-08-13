# FrameFlow 任务包目录

> 状态：**P0 工程基座已完成，业务功能进度为 0**。本目录是任务包清单；机器可读定义见 `tasks/<阶段>/<taskId>.json`，格式见 `task-capsule-spec.md` 与 `schemas/`。
> 任务状态：`NOT_READY`/`CONTRACT_READY`（禁止派发）/`READY_FOR_DISPATCH`（前置与授权已满足，可派发）/`PLANNED`（仅规划，文件未创建）。

## 1. P0-Prep（FF-PP-001）：文档收敛门禁——PASSED

```text
目标：仓库角色、服务归属、编号体系、任务协议 v2、阶段基线、包卫生全部定稿并由校验器形成证据
```

- [x] 仓库模式 A 口径一致（README/status/catalog） —— owner=规划者，evidence=EV-FF-PP-001-01
- [x] 服务归属与 AI Worker 所有权定稿且全库一致 —— evidence=EV-FF-PP-001-01
- [x] 编号体系（方案 A）、三个 Schema 与注册表落地，PP/P0/M01 实例通过真实校验 —— evidence=EV-FF-PP-001-02
- [x] 阶段基线唯一：JDK 17、MinIO=M4-B、Testcontainers 随首次引入 —— evidence=EV-FF-PP-001-03
- [x] FF-P0-001 可复现（固定版本、健康契约、等待逻辑、安全回滚） —— evidence=EV-FF-PP-001-04
- [x] 实际任务文件、状态、依赖、catalog 与 generated 索引一致；PLANNED 无任务文件且禁止派发 —— evidence=EV-FF-PP-001-02
- [x] `.DS_Store`/`__MACOSX`/个人绝对路径不进入交付；本地 `.zcode` 被 Git/打包排除且非权威 —— evidence=EV-FF-PP-001-05

**结论：P0-Prep 已通过**。2026-08-13 运行 `python3 scripts/validate_p0_prep.py`，五类门禁 37/37 项 PASS，并生成 `EV-FF-PP-001-01`～`05` 原始证据。由于 PP 同时建立 Git 前治理基线和控制面，当时没有 Grant/Receipt 或不可变前置 baseline；没有补造历史对象，透明边界记录在 `evidence/prep/p0-prep-bootstrap-audit.md`。该一次性例外仅限 PP，不可复用。用户已授权 Git 初始化与执行 P0，必要环境预检已通过，`FF-P0-001` 已完成并处于 `DONE`。

## 2. 任务包总表

| Task ID | 阶段 | 轨道 | 状态 | 目标 | 任务文件 |
|---|---|---|---|---|---|
| FF-PP-001 | P0-Prep | prep | DONE | 文档收敛门禁 | `tasks/PP/FF-PP-001.json` |
| FF-P0-001 | P0 | product-mainline | DONE | Git、Maven 多模块、Spring Boot 3.4.5、PostgreSQL、Flyway、health/readiness | `tasks/P0/FF-P0-001.json` |
| FF-M01-001 | M01 | product-mainline | CONTRACT_READY | 用户、团队、角色、JWT、RBAC（OpenAPI 已定稿，P0 验收后 READY_FOR_DISPATCH） | `tasks/M01/FF-M01-001.json` |
| FF-M02-001 | M2 | product-mainline | PLANNED | 客户、项目、Brief 版本、状态机、事务 | `tasks/M2/FF-M02-001.json` |
| FF-M03-001 | M3 | product-mainline | PLANNED | 任务、评论、乐观锁 | `tasks/M3/FF-M03-001.json` |
| FF-M04A-001 | M4-A | product-mainline | PLANNED | 素材/版本、StoragePort、本地上传 | `tasks/M4A/FF-M04A-001.json` |
| FF-M04B-001 | M4-B | product-mainline | PLANNED | MinIO Adapter、预签名 URL | `tasks/M4B/FF-M04B-001.json` |
| FF-M05-001 | M5 | product-mainline | PLANNED | Redis 缓存、幂等、限流 | `tasks/M5/FF-M05-001.json` |
| FF-M06-001 | M6 | product-mainline | PLANNED | RabbitMQ 可靠任务、重试、DLQ | `tasks/M6/FF-M06-001.json` |
| FF-M07-001 | M7 | product-mainline | PLANNED | AI Provider Adapter、suggestion | `tasks/M7/FF-M07-001.json` |
| FF-M08-001 | M8 | product-mainline | PLANNED | 审核、批注、交付锁定、E2E | `tasks/M8/FF-M08-001.json` |
| FF-M09-001 | M9 | engineering-lab | PLANNED | DDD 领域重构 | `tasks/M9/FF-M09-001.json` |
| FF-M10-001 | M10 | engineering-lab | PLANNED | PostgreSQL 深化（JSONB/GIN/EXPLAIN） | `tasks/M10/FF-M10-001.json` |
| FF-M11-001 | M11 | engineering-lab | PLANNED | Kafka/Outbox 领域事件 | `tasks/M11/FF-M11-001.json` |
| FF-M12A-001 | M12-A | engineering-lab | PLANNED | 高风险业务测试 | `tasks/M12A/FF-M12A-001.json` |
| FF-M12B-001 | M12-B | engineering-lab | PLANNED | Testcontainers 体系强化 | `tasks/M12B/FF-M12B-001.json` |
| FF-M12C-001 | M12-C | engineering-lab | PLANNED | k6 压测基线 | `tasks/M12C/FF-M12C-001.json` |
| FF-M12D-001 | M12-D | engineering-lab | PLANNED | JVM/并发故障实验 | `tasks/M12D/FF-M12D-001.json` |
| FF-M13-001 | M13 | engineering-lab | PLANNED | 微服务拆分 | `tasks/M13/FF-M13-001.json` |
| FF-M14-001 | M14 | engineering-lab | PLANNED | Feign、超时、熔断、降级 | `tasks/M14/FF-M14-001.json` |
| FF-M15-001 | M15 | engineering-lab | PLANNED | OTel、Prometheus、Grafana、ELK | `tasks/M15/FF-M15-001.json` |
| FF-M16-001 | M16 | engineering-lab | PLANNED | Docker、Helm、Kubernetes | `tasks/M16/FF-M16-001.json` |
| FF-M16G-001 | M16-G | engineering-lab | PLANNED | Istio 灰度、mTLS | `tasks/M16G/FF-M16G-001.json` |
| FF-M17-001 | M17 | engineering-lab | PLANNED | 证据收敛与求职交付 | `tasks/M17/FF-M17-001.json` |

## 3. 派发规则

```text
1. FF-PP-001 全部验收勾选并有证据后，P0-Prep 才 PASSED。
2. product-mainline 任务按顺序派发，不可跳过；前置任务 DONE 才允许下一个。
3. engineering-lab 任务按依赖拓扑选择执行，未选阶段不阻塞产品主线。
4. NOT_READY 任务任何工具不得派发；PLANNED 不是可派发输入。
5. 每个任务派发前生成 Capability Grant，并验证它是 Task Capsule 权限子集；执行后回写 Agent Receipt。
6. 执行者 writeSet 不得包含当前 Task Capsule；任务状态由调度器依据合法 Receipt、验收和证据回写。
7. 任务完成后：验收全 PASS → 证据回写 evidence-index.md → 更新 project-status.md。
8. FF-PP-001 仅按已披露的 pre-Git/control-plane bootstrap 例外保持 DONE；该例外不可用于任何其他任务。
```

## 4. 当前状态

- 已有规范任务文件：FF-PP-001（DONE）、FF-P0-001（DONE）、FF-M01-001（CONTRACT_READY）。
- 其余为 PLANNED，派发前按同一 schema 创建。
- 自动生成的索引见 `generated/task-capsule-index.md`。
