# 2026-09-13 源码修复与 GitHub 提交检查

## 结论与范围

本轮针对 2026-09-13 检查确认的缺陷完成修复，同时整合工作树已有的历史批次入口，
重写 README 并同步源码导读。检查基线为 `072fb10` 加进入本轮前的未提交修改；
最终代码以包含本报告的 Git 提交为准。

本报告列出本轮发现的全部问题，不宣称已穷尽项目中的所有潜在 Bug。
当前阶段仍是本地功能版 / 上线前验证，不是生产发布、真实客户试点或价值证明。
本轮真实 Provider 请求为 **0**；所有新建账户、候选与视频均为合成测试用途。

## 缺陷清单与修复方法

| ID | 优先级 | 原问题 | 最小修复与验证 |
| --- | --- | --- | --- |
| B01 | 高 | `progressOf` 授权藏在缓存 loader 中，热缓存跳过团队检查 | 在缓存读取前查询归属并授权；真实 Redis 的冷/热缓存跨团队请求均拒绝 |
| B02 | 高 | 创建批次只校验项目权限，未校验 Profile 与项目团队一致 | 解析版本前检查配置归属；默认最新与指定版本两条跨团队入口均返回 404，不创建批次 |
| B03 | 中 | 上传容量“先计数后插入”无并发保护，可超容量或与关闭交错 | 事务内锁定批次行后检查 OPEN 与容量；两路并发容量 1 的登记只允许一次 201，另一次 409 |
| B04 | 中 | 上传登记没有所描述的事务，存储凭证失败留下数据库占位 | 增加事务；提交后失效缓存；回滚补偿分片会话。注入存储故障后候选数为 0，再上传可成功 |
| B05 | 中 | Web 先登记再发现不支持分片，大文件占用名额；上传按钮可重复触发 | 前端预检大小/类型/空文件并防重复点击；后端 `simpleOnly=true` 按实际阈值在落库前拒绝。验证低于前端上限但超过服务端阈值的情况 |
| B06 | 中 | Web 只取前 200 条候选，没有翻页 | 每页 50 条、显示总数、上一页/下一页、旧响应失效保护；后端 300 条分页与浏览器第 201/300 条检查 |
| B07 | 中 | Profile 发布唯一冲突后在 PostgreSQL 已失败的事务中重试 | 配置主行锁串行分配版本；四路并发发布分别得到 v2–v5 |
| B08 | 严重公告 | Next.js 15.5.23 命中当前安全公告，CI audit 不通过 | 固定到同系列补丁 15.5.25，重新安装 lockfile、审计、构建和浏览器回归 |
| B09 | 高危公告 | sharp 0.35.3 命中 libheif 安全公告 | override 固定到 0.35.4；生产依赖 audit 0 条漏洞 |
| B10 | 低 / 工具 | Smoke 固定 Compose project，不能检查隔离的本地回归栈 | 增加仅 local 可用的 `--project-name frameflow-*`；非法名称负例与隔离栈动态 Smoke 通过 |
| B11 | 低 / 工具 | 演示 Worker 入口依赖旧会话 `.tmp/pre-f9-bin` | 优先原生 ffprobe，缺失时检查本地固定镜像并建立 Docker shim；无环境则明确失败。完成 shell 语法检查，推荐入口仍是 Compose |
| B12 | 低 / 界面 | 浏览器请求网站图标得到 404 | 增加 `app/icon.svg`，重新构建后验证控制台无错误 |

B08/B09 是依赖公告命中，不表示已证明本项目部署可以被利用。
来源：[Next.js 公告](https://github.com/advisories/GHSA-2xp9-vwfh-vxw4)、
[sharp 公告](https://github.com/advisories/GHSA-rgj7-g3m4-5g8c)；版本选择以 npm 官方源
可用补丁及更新后的 audit 结果为依据，未改变 Next.js 主版本或 Java 基线。

## 验证结果

| 检查 | 本轮结果 | 边界 |
| --- | --- | --- |
| JDK 17 `./mvnw -B -q verify` | 85 tests，0 failures / errors / skipped | 使用真实 PostgreSQL、Redis、MinIO、RabbitMQ Testcontainers；包含新增负例与并发测试 |
| Worker + Pilot `pytest` | 131 passed（Worker 110 + Pilot 21） | 离线测试，无真实模型调用 |
| Web 契约 | 17 passed | 包括文件大小边界；不能替代浏览器 |
| Web 类型与生产构建 | PASS | Next.js 15.5.25，最终图标变更还通过容器构建 |
| npm 生产依赖审计 | 0 vulnerabilities | 审计时点为 2026-09-13，不是长期或全技术栈无漏洞承诺 |
| App / Worker / Web 镜像 | 本地源码构建 PASS | 无 Registry 发布 |
| 完整确定性产品链 | 80 checks PASS | 合成媒体 → API → MinIO → MQ → Worker → 回写 → 聚类/排名 → 人工优选 → 锁定 → JSON/CSV |
| Compose Smoke | PASS | `frameflow-review-20260913` 隔离项目；使用 loopback |
| 部署配置与安全脚本 | PASS | 环境隔离、manifest、篡改拒绝、smoke project 名称负例 |
| 监控/备份/故障工具 | 静态与安全校验 PASS | 本轮未重新做 production 或完整 F10 动态演练 |
| 浏览器回归 | Chrome + Playwright，1440×1000 / 390×844 | 300 条分页、历史入口、大文件零登记、后续正常合成上传；无页面/控制台错误 |
| Git diff 与 README 相对链接 | 检查通过 | README 不引用本机绝对路径 |
| 新增/变更文件凭据模式检查 | 未发现匹配 | 只是有限模式扫描，不等于完整秘密审计 |

本轮 Browser plugin 不可用，采用已安装 Playwright 和本机 Chrome，无新增浏览器依赖。
浏览器中正常上传使用真实合成 MP4；用于分页的 300 条是合成候选登记记录，不冒充
300 条完整媒体分析压测。第一页到第六页、移动端返回第五页、超限文件不登记、
随后正常上传与项目历史批次入口均经过交互验证。

首次浏览器检查发现图标 404；修复后复验。首次 Smoke 使用默认项目名而实际运行在
隔离项目，未通过；修复 B10 并明确传入隔离 project 后通过，不把首次失败记为 PASS。

## 证据文件

见 [本轮证据目录](bugfix-20260913/)：脱敏产品 receipt、浏览器摘要、测试统计、
生产依赖 audit 摘要与截图。`SHA256SUMS` 记录保留文件的字节校验值。
原始带内部日志的测试输出仅留本地，认证令牌、密码与预签名 URL 不进入本目录。

## 尚未完成与剩余风险

1. Web 分片/断点续传/批量拖放、账户编辑/密码恢复、完整团队管理未实现；
   本轮修复明确拒绝不支持的输入，没有用假按钮假装实现这些功能。
2. 项目历史批次列表仍一次返回全部批次；批次很多时需要分页和批量统计优化。
3. 数据库事务与 MQ 发布仍有跨系统失败窗口；大批量/故障场景需要继续评估 Outbox 等方案，
   本轮未改异步架构，也未声称消除所有重复计算或丢失回写窗口。
4. 大文件 API 支持不代表所有体积在默认 Worker 临时空间下都已压测；上传中断后的恢复与
   对账流程仍以现有 API/运维工具为主，没有完整的 Web 自助续传体验。
5. 生产部署、远程告警/恢复、真实客户准确率/成本/价值仍待验证；历史真实 Provider 测试
   仅代表当次合成数据运行。历史凭据是否已轮换，本轮未读取或核验。
6. LICENSE 尚未由所有者选择；源码可以提交到 GitHub，不据此声称已经完成开源许可授权。
7. GitHub Actions 状态必须查看对应提交的实际运行。本地 PASS 不能代替远程 CI PASS，
   源码 push 不等于镜像发布、部署或 GitHub Release。

## 源码阅读

- F2：`QualityProfileService.publishVersion` → `QualityProfileMapper.findProfileByIdForUpdate`。
- F3：`BatchService.create/registerCandidate` → `BatchMapper.findByIdForUpdate` → `StoragePort`。
- F5：`BatchService.progressOf` → `ProgressCacheService.getOrLoad`，理解“先授权、后缓存”。
- F8：`lib/batch-upload.ts` → `app/batches/[id]/page.tsx`，理解预检、分页与请求竞态保护。
- 测试：`BatchUploadFlowIntegrationTest`、`RedisFlowIntegrationTest`、`ProductFlowIntegrationTest`、`tests/batch-upload.test.ts`。

完整产品说明、已实现/未实现功能与阶段路线见 [README](../../README.md)。
