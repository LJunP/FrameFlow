# F6 完整产品真实 Provider E2E（修复后重试，2026-08-25）

> 结论：`PASS`。这是合成媒体的本地完整产品链证据，不是生产发布、真实客户试点或价值证明。

## 执行边界

- 分类：`REAL_PROVIDER_FULL_PRODUCT_E2E_SYNTHETIC_ONLY`
- 输入：项目生成的 6 秒、1280×720、24 fps 合成 MP4；真实客户数据为 0
- Provider：OpenCode Go / `gpt-5.6-luna` / Responses `/responses`
- 本次外部请求：恰好 1 次；`providerRequestOrdinal=1`、
  `providerRequestBudget=1`，自动重试 0
- 请求入口源码提交：`1fad2b6dc0e2`
- 当前 HEAD 镜像：
  - App `sha256:c6d979c776a526d542973f04728a5d0cdbc715799d4cd10cbfcb0ae6bc5584d4`
  - Worker `sha256:8f31113443574a1828f4c4240f95e7106449e3c05b145d911d11a7f07a087140`
  - Web `sha256:bdfa18c597a56e25e1b7b8a9e4f144b671cb2104c55d658481b0dea6bdb67e9a`
- Key 通过隐藏输入临时写入当前用户所有的 `0600` 文件，并且只注入 Worker；
  App/Web 均没有该变量。执行后文件与临时目录已永久删除

## FACT

1. 修复后的 144 帧夹具在上传前复用产品检测器，黑帧 0、冻结 0；Worker 全套
   离线测试为 `110 passed`。
2. API、Web smoke、认证、Profile/Brief 快照、MinIO 上传、批次关闭和 RabbitMQ
   单任务派发全部通过。
3. Worker 完成消息恰好 1 条且 `delivery_attempt=1`；数据库 Analysis Run 为
   `SUCCEEDED`，候选状态为 `ANALYZED`，Java 持久化 8 条 Finding。
4. Luna 返回 3 条语义结果，全部为 `PASS`；它准确读出未出现在 Brief 中的
   `FF-E2E-9Q2M-7K4X`，并正确判断 1/3→3/3 阶段和三角形移动。
5. 排名结果为 1 个候选、1 个聚类、排名第 1、分数 100；Selection `topK=1`、
   `machinePick=true` 并进入 `LOCKED`。
6. JSON/CSV 各导出 1 行并保存 SHA-256；门禁共 39 项检查、失败 0，Receipt 为
   `PASS`。
7. 结束后本次 Compose 项目的容器、卷、网络和门禁监听端口均为 0；临时 Key 与
   运行目录不存在。没有推送、部署、SSH、DNS 或生产操作。

## INFERENCE

- 在本地合成输入边界内，OpenCode Go Responses 协议、Luna 多模态理解、MinIO、
  RabbitMQ、Worker、Java 回写、排名、锁定与导出已经形成同一次运行的闭环证据。
- 请求前夹具预检有效阻止了上一轮“先消耗 Provider、再被本地冻结规则拒绝”的问题。

## UNKNOWN

- 远程 CI、Registry Digest、VPS/HTTPS/production 发布与 production 恢复仍未执行。
- 真实客户媒体、供应商隐私/地域/合同、真实人工盲评、成本、长期稳定性和试点价值
  仍未知；本次证据不得用于设置 `PROJECT_COMPLETE` 或 `PILOT_VALUE_PROVEN`。

## Artifact

| 文件 | bytes | SHA-256 | 说明 |
| --- | ---: | --- | --- |
| `receipt.json` | 7906 | `6c352331cfd73fbcff130acf06d8ce437e643a2fa1d43696ff10ee9c68b509cc` | 39 项完整产品门禁 Receipt |
| `persisted-result.json` | 5135 | `e497094c2cb7f1aef981f7f1f12435024516de22e3657a758736fef590934cb8` | Java/PostgreSQL 持久化脱敏快照 |
| `worker-message.jsonl` | 163 | `398f1b06d62db22ede0fb2474973c82762a8ddc8a1fa472a9d4cefe86b63e343` | 单条 Worker 完成事件 |
| `media/synthetic-visual-challenge.mp4` | 2756334 | `3be99862a42a70942bd78e0ea88b4ea466b51c00a97deceae254606f2737bb63` | 本地合成媒体；按 Evidence 媒体策略不提交 Git |

截图中曾暴露供应商 Key，所有者此前选择暂不轮换；这不构成凭据安全 PASS，生产或
真实客户使用前仍建议撤销/轮换。本次 Evidence 不含 Key、请求头或真实客户媒体。
