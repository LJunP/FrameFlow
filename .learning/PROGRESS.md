# FrameFlow Select · 开发与学习进度

> 计划见 `docs/03-开发与学习路线.md`（功能切片 F1–F11）。
> 开发状态由 Agent 在功能交付时更新；**学习状态只能由项目所有者本人更新**
> （未读 / 阅读中 / 已理解）。源码导读在 `docs/guides/F<n>-源码导读.md`。

## 当前状态

- 当前功能：F1 工程基线与用户认证
- 开发状态：未开始（等 Agent 开工 `feature/f1-工程基线` 分支）
- 学习状态：未读
- 更新日期：2026-08-22

## 功能进度

> 开发 = Agent 代码交付情况；学习 = 所有者阅读理解情况。两列独立推进。

| 功能 | 开发状态 | 学习状态 | 导读 | 备注 |
| --- | --- | --- | --- | --- |
| F1 工程基线与用户认证 | 未开始 | 未读 | — | |
| F2 项目与质检配置管理 | 未开始 | 未读 | — | |
| F3 批次与视频上传 | 未开始 | 未读 | — | |
| F4 确定性质检流水线 | 未开始 | 未读 | — | |
| F5 缓存与限流（Redis） | 未开始 | 未读 | — | 可在 F4 后穿插 |
| F6 语义质检（AI Provider） | 未开始 | 未读 | — | |
| F7 聚类排名与 Top-K 优选 | 未开始 | 未读 | — | |
| F8 Web 前端产品化 | 未开始 | 未读 | — | 可与 F6/F7 并行 |
| F9 服务器部署与 CI/CD | 未开始 | 未读 | — | 物理操作需所有者执行 |
| F10 生产化运维 | 未开始 | 未读 | — | |
| F11 真实试点验证 | 未开始 | — | — | 价值结论由所有者批准 |

## 任务明细

### F1 工程基线与用户认证

- [ ] T1 ping 接口 + 配置读取 + 单测/集成测试
- [ ] T2 用户/团队/成员表与 Flyway 迁移
- [ ] T3 注册/登录/刷新 Token（RS256）
- [ ] T4 受保护接口示例 + 越权负例
- [ ] T5 请求幂等记录
- [ ] 交付《F1 源码导读》（docs/guides/F1-源码导读.md）

## 技术学习清单

> 所有者阅读对应功能源码与导读、能回答 docs/03 的自测问题后，自行把状态
> 从 not_started 改为 understood。Agent 不得代改。

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
