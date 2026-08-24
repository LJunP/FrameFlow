# Runbook: 分析队列积压

1. 同时看 ready、unacked、consumer 数和 Worker 成功/失败速率。
2. ready 增长且 consumer=0：按 WorkerDown；unacked 长时间不降：检查卡住任务。
3. 不直接 purge 业务队列。先保存队列统计、消息年龄、发布与消费速率。
4. 扩容前确认瓶颈不是 Provider 限流、MinIO 或 API 回写；盲目扩 Worker 会放大故障。
5. DLQ 重放必须走已有管理接口和人工审计，不把毒消息无限循环。
