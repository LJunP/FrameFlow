# SYNTHETIC_REHEARSAL · F11 合成试点彩排报告

> **边界：这是合成数据工程彩排，不是现实试点，也不具备真实价值结论资格。**
> 本次没有客户媒体、个人数据或真实 Provider 调用。

## Provenance

- rehearsalId：`syn-2411-6794ad834155f195`
- datasetId：`syn-pilot-20260824-seed-2411`
- seed：`2411`
- source commit：`62f6c70ddf659cf4aa89088bc8e019b9a6576205`
- input set SHA-256：`6794ad834155f1957418420ab55da51f4892690981773e1e0d44a260ed1889c8`

## FACT（已由本地 Evidence 验证）

- 本地已生成合成视频、关键帧图片、Brief、Profile、标注、重复/异常媒体与 300 记录压力 manifest。
- Package validator 已检查 hash、路径约束、盲评字段隔离、异常媒体 probe 与真实 Provider 禁用策略。
- 指标包含零分母守卫与缺失计数；时长节省使用成对 bootstrap 区间，二元比例使用 Wilson 区间。
- 300 记录压力批次只验证 manifest/工具容量；它没有上传到应用，也不是吞吐量测量。
- 处理时长是合成场景输入，不是应用墙钟实测。

## 合成指标（只验证计算口径）

| 指标 | 值 | 95% 区间 | 统计基数 | 限制 |
|---|---:|---:|---:|---|
| 人工观看时长节省率（主指标） | 59.5% | 32.0%–85.8% | 11.0/18.5 秒 | 小样本 |
| 完整观看规避率（辅助） | 50.0% | 23.7%–76.3% | 5/10 | 小样本 |
| 误杀率 | 50.0% | 15.0%–85.0% | 2/4 | 小样本 |
| 漏检率 | 50.0% | 9.5%–90.5% | 1/2 | 小样本 |

- 盲评一致性：raw agreement `77.8%`，Cohen's κ `0.690`，可比样本 `9`。
- 处理时长中位数：`42.0` 秒（合成输入，不是应用实测）。
- 分母低于 30 的比例均带小样本警告；逐项以机器报告为准。

## INFERENCE（工程推断）

- F11 本地数据契约、标注、指标与报告链已具备接收合规授权真实试点包的工程条件。
- 非平凡合成比例和 Reviewer 分歧实际覆盖了警告/裁决路径，没有制造全绿结果。

## UNKNOWN（真实试点仍待验证）

- 真实客户批次构成、权利/同意记录与留存义务。
- 真实 Provider 的输出质量、延迟与成本；当前没有发生真实调用。
- Operator 实际观看行为、误杀率、交付后漏检率与端到端处理时长。
- 真实试点是否能在足够分母下满足预注册成功阈值。

## 复现

```bash
python3 scripts/pilot/run_synthetic_rehearsal.py
python3 -m unittest discover -s tests/pilot -v
```

完整逐文件 SHA-256 与执行环境见同目录 `receipt.json`、`provenance.json` 和
`artifact-checksums.sha256`。
