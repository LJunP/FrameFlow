# F9 前正确性门禁夹具

执行：

```bash
python3 scripts/gen_test_media.py \
  --out experiments/fixtures/generated/pre-f9-correctness \
  --suite pre-f9 --require-real
```

生成物均为程序合成媒体，不含客户素材：

| 文件 | 预期用途 |
|---|---|
| `valid-motion-a.mp4` | 合格候选 |
| `valid-motion-a-exact-copy.mp4` | 与 A 字节完全相同，用于精确重复聚类 |
| `valid-motion-b.mp4` | 内容不同的合格候选 |
| `too-short.mp4` | 时长规则触发确定性 `AUTO_REJECT` |
| `invalid-container.mp4` | 只有 MP4 入口签名，深度探针必须进入 `ANALYSIS_ERROR` |

`deterministic-profile.json` 用于不调用 Provider 的完整业务闭环；
`semantic-profile.json` 只在真实 Provider 调用获得所有者再次确认后使用。
`model-catalog.example.json` 是平台双模型目录的无密钥样例；其中供应商地址、
真实模型名和两个 Key 环境变量都只是占位符，直接使用不会形成真实调用。
`model-catalog.opencode-go-luna.example.json` 是 OpenCode Go Luna 的固定无密钥路由；
只有 `scripts/run_real_provider_gate.py --mode live` 在精确确认、0600 Key 文件和全合成
视觉挑战同时满足时才允许发出一次请求，且不自动重试。完整步骤见
`docs/guides/F6-真实Provider门禁.md`。2026-08-24 的唯一一次真实请求 Evidence 位于
`docs/evidence/f6-real-provider-opencode-luna-2026-08-24/`；再次执行必须重新授权。

完整本地 API 门禁（前提是 Compose、Java API 与 Python Worker 已启动）：

```bash
FRAMEFLOW_GATE_PASSWORD='仅用于本地的测试密码' \
python3.11 scripts/run_pre_f9_api_gate.py \
  --api-base http://127.0.0.1:18080/api/v1
```

脚本默认只允许 loopback、只读取 `deterministic-profile.json`，stdout 只输出
一份脱敏 JSON receipt；任一状态、Finding、聚类、人工动作或导出契约不符
都会非零退出。它会留下合成本地测试数据，不自动清理。

这些夹具只能证明工程链路行为，不能证明真实客户价值或
`PILOT_VALUE_PROVEN`。
