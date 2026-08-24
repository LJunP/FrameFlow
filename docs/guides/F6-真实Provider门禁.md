# F6 · 真实 Provider 受控门禁

> 本文是未来执行 Runbook，不是已经发生的真实调用记录。第一次只允许合成媒体；
> 任何 API Key 都不得进入聊天、截图、Git、主 Compose env、Java/Web 或证据。

## 1. 当前目标模型

- 平台：OpenCode Go；
- 逻辑 ID：`opencode-go-luna-v1`；
- 实际模型：`gpt-5.6-luna`；
- Provider 协议：`openai-responses`；
- Base URL：`https://opencode.ai/zen/go/v1`；
- 请求路径由 Worker 追加：`/responses`。

无密钥目录模板：
`experiments/fixtures/pre-f9-correctness/model-catalog.opencode-go-luna.example.json`。

## 2. 执行前硬门禁

1. 撤销任何曾出现在截图、聊天或日志中的旧 Key，并生成新 Key；
2. 新 Key 不通过聊天传给 Agent，不作为命令行参数，也不写入仓库；
3. 第一次只发送项目生成的合成视频/关键帧，真实客户媒体数量必须为 0；
4. 所有者发送明确的“授权执行一次受控真实 Provider 多模态门禁”后才能联网；
5. [OpenCode Go 当前公开说明](https://opencode.ai/docs/go/#privacy)为该模型保留滥用
   监控数据最长 30 天；真实客户试点前必须重新确认当时政策，并完成隐私、地域、
   合同和客户授权决策。

若所有者明确拒绝轮换已暴露 Key，本项目不能把这种选择记录为安全 PASS。一次性门禁
执行器要求额外传入 `--ack-exposed-key-risk`，并在 Evidence 中永久记录
`exposedCredentialRotationDeclined=true`；这只表示所有者接受本次风险，不会消除
撤销旧 Key 的安全建议。

## 3. 安全安装 Key（未来由所有者执行）

先把模板复制为环境自己的无密钥目录，确认里面只有公开配置。然后运行：

```bash
infra/scripts/capture-provider-secrets.sh \
  --catalog /ABS/PATH/model-catalog.json \
  --output /ABS/PATH/worker-provider.env
```

脚本根据 enabled 目录项逐个隐藏读取 Key，最终文件为 `0600`；已有文件默认拒绝
覆盖。主环境文件只保存：

```text
FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON=<单行无密钥 JSON>
FRAMEFLOW_WORKER_PROVIDER_ENV_FILE=/ABS/PATH/worker-provider.env
```

随后运行：

```bash
infra/scripts/check-env-isolation.sh \
  --environment local \
  --env-file /ABS/PATH/frameflow.env
```

校验器会核对绝对路径、owner、`0600`、非 symlink、enabled `apiKeyEnv` 精确键集。
Compose 只把该 env-file 注入 Worker，App/Web 无法读取其中的值。

## 4. 获得授权后的验收证据

先执行零网络 dry-run，确认挑战码不在 prompt、Adapter 恰好调用一次且外部请求为 0：

```bash
frameflow-ai-worker/.venv/bin/python scripts/run_real_provider_gate.py \
  --mode dry-run \
  --evidence-dir /ABS/NEW/PATH/offline-gate-evidence
```

真实执行使用同一个生产 Responses Adapter，但注入 one-shot transport；Evidence 目录
必须预先不存在，避免覆盖历史。若本次按所有者决定复用已暴露 Key，命令为：

```bash
frameflow-ai-worker/.venv/bin/python scripts/run_real_provider_gate.py \
  --mode live \
  --provider-env-file /ABS/PATH/worker-provider.env \
  --evidence-dir /ABS/NEW/PATH/real-gate-evidence \
  --confirm LIVE_PROVIDER_SYNTHETIC_ONE_REQUEST \
  --ack-exposed-key-risk
```

执行器会生成 3 张 JPEG，随机挑战码只写入图片、不写入 prompt。只有模型在
`visual_challenge` reason 中逐字返回该码，并正确识别 1/3→3/3 与橙色三角形移动，
门禁才 PASS。任何失败都不自动重试；第二次真实请求必须取得新的明确授权。

- 实际请求为 `/responses`，含 1–3 个字节可追溯的 `input_image` JPEG；
- 模型返回可解析的 `output_text`，每个请求维度都有 PASS/VIOLATE/UNKNOWN；
- Evidence 只含 `modelId`、实际 `model`、prompt、输出、帧时间码与帧 SHA-256；
- Key、Key 环境变量名与 Base URL 均不进入产品证据或日志；
- 记录 HTTP 结果、端到端延迟、token/费用（若供应商返回）、失败语义与人工复核状态；
- Provider 故障的 ERROR 降级由离线 Stub 负例验证；真实 one-shot 门禁不为了制造
  负例再浪费第二次联网请求；
- 测试完成后停止服务，删除合成运行时 Secret 文件或按所有者选择保留在受控路径。

只有这些证据真实产生后，才能把“真实 Provider 调用记录”由未执行改为已执行；
它仍不等于生产部署、真实客户试点或产品价值证明。
