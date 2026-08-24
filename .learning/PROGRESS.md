# FrameFlow Select · 开发与学习进度

> 计划见 `docs/03-开发与学习路线.md`（功能切片 F1–F11）；品牌前台与账户团队基础作为 F12 独立切片交付。
> 开发状态由 Agent 在功能交付时更新；**学习状态只能由项目所有者本人更新**
> （未读 / 阅读中 / 已理解）。源码导读在 `docs/guides/F<n>-源码导读.md`。

## 当前状态

- 已交付：F1–F8 开发切片及 F6.1 平台多模型选择扩展（不等于产品、上线或试点全部完成）
- 下一正式功能：F9 服务器部署与 CI/CD；当前暂缓。Profile 契约、多模态图像输入、平台模型目录/用户选择、探针缺失值语义等代码阻断已修复，门禁只剩一次经所有者确认的真实 Provider 调用记录
- 开发状态：F8 已交付；F12 品牌前台与账户团队基础已完成当前工作树回归，等待所有者验收
- F12 范围：公开首页、认证体验、工作台导航、个人中心、当前团队只读页、业务化质量规则表单（含平台多模态模型选择）
- F12 导读：[F12 源码导读](../docs/guides/F12-源码导读.md)
- F12 学习状态：未读（由所有者本人更新）
- 学习状态：F1–F8、F6.1、F12 均未读（只能由所有者本人更新）
- 当前门禁事实：合成真 MP4 已跑通 API → MinIO → RabbitMQ → Python Worker → Java 回写 → 排名/聚类 → 人工调整 → 锁定 → JSON/CSV 导出；真实 Provider 按所有者边界尚未调用
- F6.1 当前事实：平台无密钥模型目录、受保护安全 API、Profile 版本化选择、Worker 精确路由、证据脱敏和前端选择器的自动化均通过；未做真实 Provider 调用
- 待办：所有者确认真实 Provider 调用并由平台注入目录/Worker Key 后，用 `semantic-profile.json` 与合成视频补一次所选模型的多模态调用记录；通过后再进入 F9
- 更新日期：2026-08-23

## 功能进度

> 开发 = Agent 代码交付情况；学习 = 所有者阅读理解情况。两列独立推进。

| 功能 | 开发状态 | 学习状态 | 导读 | 备注 |
| --- | --- | --- | --- | --- |
| F1 工程基线与用户认证 | 已交付 | 未读 | [F1](../docs/guides/F1-源码导读.md) | 17 测试全绿 |
| F2 项目与质检配置管理 | 已交付 | 未读 | [F2](../docs/guides/F2-源码导读.md) | 30 测试全绿（累计） |
| F3 批次与视频上传 | 已交付 | 未读 | [F3](../docs/guides/F3-源码导读.md) | 38 测试全绿（累计） |
| F4 确定性质检流水线 | 已交付 | 未读 | [F4](../docs/guides/F4-源码导读.md) | 当前全量 Java 68 + Worker 77 测试全绿；合成真媒体主链通过 |
| F5 缓存与限流（Redis） | 已交付 | 未读 | [F5](../docs/guides/F5-源码导读.md) | 50 测试全绿；降级演练实测 |
| F6 语义质检（AI Provider） | 已交付（待真实调用） | 未读 | [F6](../docs/guides/F6-源码导读.md) | 多模态帧输入、证据哈希、错误降级和多模型路由已测试；真实调用记录未执行 |
| F6.1 平台多模型选择 | 已交付（自动化通过） | 未读 | [F6.1](../docs/guides/F6.1-源码导读.md) | 平台托管 Key，用户选 enabled 模型；Java 68、Worker 77、Web 契约 14 全绿 |
| F7 聚类排名与 Top-K 优选 | 已交付 | 未读 | [F7](../docs/guides/F7-源码导读.md) | 58+33 全绿 |
| F8 Web 前端产品化 | 已交付（合成全链回归通过） | 未读 | [F8](../docs/guides/F8-源码导读.md) | 合成真媒体全链与真实浏览器深链通过；不等于真实 Provider/试点价值证明 |
| F12 品牌前台与账户团队基础 | 回归完成（待验收） | 未读 | [F12](../docs/guides/F12-源码导读.md) | 登录/登出、团队/账户、工作台深链及 390px 焦点管理当前实测通过 |
| F9 服务器部署与 CI/CD | 暂缓（门禁仅余真实 Provider） | 未读 | — | 真实 Provider 调用需所有者再次确认；物理操作仍需所有者执行 |
| F10 生产化运维 | 未开始 | 未读 | — | |
| F11 真实试点验证 | 未开始 | — | — | 价值结论由所有者批准 |

## 任务明细

### F1 工程基线与用户认证（开发完成 2026-08-22）

- [x] T1 ping 接口 + 配置读取 + 单测/集成测试
- [x] T2 用户/团队/成员表与 Flyway 迁移（V1：5 张表）
- [x] T3 注册/登录/刷新 Token（RS256 + BCrypt + 刷新轮换）
- [x] T4 受保护接口 + 401/404/403 三层越权负例（11 个集成场景）
- [x] T5 请求幂等（Idempotency-Key 中间件 + ON CONFLICT 存储）
- [x] 交付《F1 源码导读》（docs/guides/F1-源码导读.md）

### F2 项目与质检配置管理（开发完成 2026-08-22）

- [x] T1 Project CRUD + 权限矩阵（乐观锁、分页、归档仅 OWNER）
- [x] T2 Brief 不可变快照（Mapper 无 UPDATE，指针前移）
- [x] T3 Quality Profile 版本化（JSONB spec，版本号只增不复用）
- [x] T4 手写 OpenAPI 契约 + 双向一致性测试 + ArchUnit 三条边界规则
- [x] 交付《F2 源码导读》（docs/guides/F2-源码导读.md）

### F3 批次与视频上传（开发完成 2026-08-22）

- [x] T1 批次创建（绑定 Profile 版本 + Brief 快照，容量 1..300 有界）
- [x] T2 Presigned 直传（SIMPLE/MULTIPART 双模式，真实 MinIO 容器验证）
- [x] T3 候选元数据入库（object_key/etag；完成时三重校验：存在/大小/媒体签名）
- [x] T4 对账（超时会话判 INVALID、missing/orphan/未确认 全量报告）
- [x] T5 可复现测试媒体脚本（scripts/gen_test_media.py，ffmpeg/占位双模式）
- [x] 交付《F3 源码导读》（docs/guides/F3-源码导读.md）

### F4 确定性质检流水线（开发完成 2026-08-22）

- [x] T1 Analysis Run 状态机 + 批次调度（条件迁移当锁，防重复派发）
- [x] T2 RabbitMQ 派发/消费骨架（Publisher Confirm + DLX/DLQ 拓扑）
- [x] T3 Python worker：消费循环 + ffprobe 探针（本机无 ffmpeg 自动 ANALYSIS_ERROR）
- [x] T4 黑帧/冻结检测器（OpenCV，纯函数可单测）+ spec 规则判定
- [x] T5 幂等回写（条件状态迁移裁决；重复回放零副作用有专测）
- [x] T6 DLQ 重放接口 + 队列深度报告（OWNER 运维接口）
- [x] 交付《F4 源码导读》（docs/guides/F4-源码导读.md）

### F5 缓存与限流（Redis）（开发完成 2026-08-22）

- [x] T1 登录限流（Lua 原子令牌桶，每邮箱容量 5、30s 回填 1）
- [x] T2 批次进度缓存（Cache-Aside + 30s TTL + 三处写后失效）
- [x] T3 穿透防护（空值哨兵 60s）+ 击穿防护（SETNX 互斥重建）
- [x] T4 宕机降级演练（实测记录：docs/evidence/F5-redis-降级演练.md）
- [x] 交付《F5 源码导读》（docs/guides/F5-源码导读.md）

### F6 语义质检（AI Provider）（开发完成 2026-08-22；F6.1 增量 2026-08-23）

- [x] T1 Provider SPI + Fake Provider（稳定哈希，评测可复现）
- [x] T2 OpenAI 兼容多模态适配器（最多 3 张真实 JPEG 以 `image_url` 发送；无 Key → 语义 ERROR）
- [x] T3 语义 Finding 证据束落库 + REVIEW_REQUIRED（BLOCKER 双侧强制降级）
- [x] T4 离线评测集 + 指标报告（基线已存档 eval/reports/）
- [x] T5 关键帧/提示词预算控制（MAX_KEYFRAMES=3、prompt 截断）
- [x] T6 平台模型目录 + 受保护安全 API + Profile `modelId` 快照 + Worker 白名单路由 + Web 选择器
- [x] 交付《F6 源码导读》（docs/guides/F6-源码导读.md）
- [x] 交付《F6.1 源码导读》（docs/guides/F6.1-源码导读.md）
- [ ] 真实 Provider 调用记录（待所有者确认与受控 Provider 配置）

### F7 聚类排名与 Top-K 优选（开发完成 2026-08-22）

- [x] T1 精确重复检测（worker 回填 SHA-256）
- [x] T2 近重复聚类（dHash + 阈值版本化 + 并查集 + 稳定代表）
- [x] T3 资格门 + 可解释加权评分 + 快照固化（可复现有专测）
- [x] T4 人工叠加接口（machine_pick/human_action 两列并存）
- [x] T5 锁定（条件更新防重入）+ JSON/CSV 导出
- [x] 交付《F7 源码导读》（docs/guides/F7-源码导读.md）

### F8 Web 前端产品化（开发完成 2026-08-22）

- [x] T1 登录态与请求层（内存 access + HttpOnly cookie refresh + 单飞刷新 + 同源代理）
- [x] T2 项目/批次列表与创建（含 Brief 快照发布、Profile 创建）
- [x] T2.1 Profile 业务表单接入平台模型目录（仅 enabled 白名单、失败时禁止 AI 配置提交）
- [x] T3 上传页（XHR 直传 MinIO + 进度条）
- [x] T4 候选审阅页（presigned 播放 + 确定性/语义双区时间线 + 点击跳画面）
- [x] T5 优选/导出页（机器/人工双列、锁定确认、CSV/JSON blob 下载）
- [x] T6 移动端基础适配（响应式表格/布局）
- [x] 历史 F8 浏览器闭环不作为当前证据；2026-08-23 已重新运行当前工作树
- [x] 当前工作树：公开页、认证、工作台、账户/团队、深层业务路由、移动菜单焦点环与 Escape 恢复实测通过
- [x] 合成真 MP4 → 分析 → 排名/重复聚类 → 人工优选 → 锁定 → JSON/CSV 导出当前 PASS（Provider 明确禁用）
- [ ] 真实 Provider 多模态调用记录：尚未执行，执行前必须按所有者要求再次通知
- [x] 交付《F8 源码导读》（docs/guides/F8-源码导读.md）

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
