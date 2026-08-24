# F11 Pilot-ready 工程资产

本目录只保存可复现的试点工程输入、Schema 与脱敏 Evidence。默认一键彩排：

```bash
python3 scripts/pilot/run_synthetic_rehearsal.py
```

输出分成两类：

- `generated/synthetic-rehearsal/`：可再生成的合成媒体、盲评样例与压力清单；
- `evidence/synthetic-rehearsal/`：机器可读 receipt/metrics/report 与 Markdown 报告。

所有合成结果必须携带 `SYNTHETIC_REHEARSAL` 分类。它们只证明数据契约、指标
边界与报告生成链可运行，不代表真实客户、真实 Provider 或线上容量证据。
