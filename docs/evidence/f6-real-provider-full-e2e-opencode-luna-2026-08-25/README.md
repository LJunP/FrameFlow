# F6 完整产品真实 Provider E2E 尝试（2026-08-25）

> 结论：`FAIL`。本次不是完整产品链 PASS，也不是生产或真实试点证据。

## 执行边界

- 分类：`REAL_PROVIDER_FULL_PRODUCT_E2E_SYNTHETIC_ONLY`
- 输入：项目生成的 6 秒、1280×720、24 fps 合成 MP4；真实客户数据为 0
- Provider：OpenCode Go / `gpt-5.6-luna` / Responses `/responses`
- 外部请求：本次恰好 1 次；`providerRequestOrdinal=1`、
  `providerRequestBudget=1`，失败后未重试
- 请求入口源码提交：`8f192a8d8b84`
- 本地镜像：
  - App `sha256:7897983b5988746730f324bebe1e5d74463b7ecd42e4fd69e8851f95cd6e0d89`
  - Worker `sha256:0ff55dcb8c1f0e056d445b13aee296eeaccca1f30c69a2726c06c4c3a26d3b41`
  - Web `sha256:2991c867f4498a85e782db34f87aa65257a8d634bb2f2b3e140031e3baf15f11`
- Key 只临时进入 Worker；App/Web 不含该变量。执行后 Compose 容器、卷、网络、
  门禁端口均为 0，`0600` Key 文件及其临时目录已永久删除

## FACT

1. API、Web、认证、Profile 模型快照、Brief 快照、MinIO 上传、批次关闭和
   RabbitMQ 单任务派发均通过。
2. Worker 的 `analysis_run=1` 状态为 `SUCCEEDED`，Java 收到并持久化了 8 条 Finding。
3. 真实模型返回了 3 条语义结果，均为 `PASS`；它准确读出未出现在 Brief 中的
   `FF-E2E-7K9Q-2M4X`，也正确判断三阶段与三角形移动。
4. 确定性规则中的时长、分辨率、帧率和黑帧检查通过；旧合成夹具被
   `opencv-frames` 判出 6 段冻结区间，产生 BLOCKER，候选因此进入 `AUTO_REJECT`。
5. 门禁在 `java_callback.terminal_status` 处按预期 fail-closed；排名、锁定和
   JSON/CSV 导出未执行，不能宣称完整产品链 E2E 通过。
6. 现场失败后未再次调用 Provider。随后修复合成视频生成器，并新增“上传前复用
   产品黑帧/冻结检测器”的 fail-closed 预检；修复提交为 `49b3629`，Worker 全套
   离线测试为 `110 passed`。

## INFERENCE

- OpenCode Go Responses 协议、Luna 多模态视觉输入、RabbitMQ Worker 调用和 Java
  回写之间已经形成强互操作证据。
- 本次总门禁失败来自门禁夹具运动量不足，而不是模型没有理解图片或 Provider
  请求失败；修复后的夹具已离线通过同一冻结检测器。

## UNKNOWN

- 修复后的夹具尚未获得新的真实请求授权，因此“真实模型→排名→锁定→导出”的
  单次完整 PASS 仍未知。
- 远程 CI、VPS/生产部署、生产网络稳定性、真实客户媒体、真实人工审核、成本与
  试点价值均未验证。

## Artifact

| 文件 | bytes | SHA-256 | 说明 |
| --- | ---: | --- | --- |
| `receipt.json` | 3504 | `bd87643e1dffd213595150f6fb15aca76212b3570a7fab6cfc8f26d6fef0df4a` | 门禁 fail-closed receipt |
| `persisted-result.json` | 4787 | `04c122aa536e567b9eb4bf3d0480d0908e44291880953d6574d332ea967f60b9` | Java/PostgreSQL 持久化结果的脱敏快照 |
| `media/synthetic-visual-challenge.mp4` | 2375614 | `dfcfdeb79c767a38ebafacea8b838f3ef9ea4cc1ee7c68458918bfe6316a416c` | 本地生成的失败夹具；按 Evidence 媒体策略不提交 Git |

截图中暴露过的供应商 Key 仍建议由所有者后续撤销/轮换；本次没有把 Key、请求头、
Base URL 或真实客户媒体写入 Evidence。
