# FrameFlow Select · 开发与学习进度

> 计划见 `docs/03-开发与学习路线.md`（功能切片 F1–F11），本文件只记录状态。
> 由项目所有者维护；每个功能完成时更新对应行与当前任务。
> Evidence 存放约定：`docs/evidence/F<n>/`（操作记录、脱敏输出、总结）。

## 当前状态

- 当前功能：F1 工程基线与用户认证
- 当前任务：F1-T1（ping 接口 + 配置读取 + 测试，走完整 feature 分支流程）
- 更新日期：2026-08-22

## 功能进度

| 功能 | 状态 | 完成日期 | 备注 |
| --- | --- | --- | --- |
| F1 工程基线与用户认证 | 进行中 | — | T1 待开始 |
| F2 项目与质检配置管理 | 未开始 | — | |
| F3 批次与视频上传 | 未开始 | — | |
| F4 确定性质检流水线 | 未开始 | — | |
| F5 缓存与限流（Redis） | 未开始 | — | 可在 F4 后穿插 |
| F6 语义质检（AI Provider） | 未开始 | — | |
| F7 聚类排名与 Top-K 优选 | 未开始 | — | |
| F8 Web 前端产品化 | 未开始 | — | 可与 F6/F7 并行 |
| F9 服务器部署与 CI/CD | 未开始 | — | |
| F10 生产化运维 | 未开始 | — | |
| F11 真实试点验证 | 未开始 | — | |

## 任务明细

### F1 工程基线与用户认证

- [ ] T1 ping 接口 + 配置读取 + 单测/集成测试（HUMAN_CORE）
- [ ] T2 用户/团队/成员表与 Flyway 迁移（HUMAN_CORE）
- [ ] T3 注册/登录/刷新 Token（RS256）（HUMAN_CORE）
- [ ] T4 受保护接口示例 + 越权负例（HUMAN_CORE）
- [ ] T5 请求幂等记录（PAIR）

## 能力掌握记录

> 格式：`技术 — 状态（not_started / learning / mastered）— 依据（功能+Evidence 链接）`
> 仅当对应功能三件套齐备且自测通过时才可记 mastered。

- Git 公司式流程 — not_started（F1）
- Java 17 + Spring Boot — not_started（F1/F2）
- PostgreSQL / Flyway / SQL — not_started（F1/F2）
- MinIO 对象存储 — not_started（F3）
- RabbitMQ 异步可靠性 — not_started（F4）
- Python 视频处理 — not_started（F4）
- Redis 工程 — not_started（F5）
- AI Provider 与评测 — not_started（F6）
- 算法（聚类/排名） — not_started（F7）
- Next.js 前后端联调 — not_started（F8）
- Linux 运维 — not_started（F9）
- Docker / Compose — not_started（F9）
- CI/CD 与晋级 — not_started（F9）
- 监控/备份/故障恢复 — not_started（F10）
- 试点与指标 — not_started（F11）
