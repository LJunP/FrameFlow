# Runbook: FrameFlowWorkerDown / WorkerFailuresBurst

1. 对比 `up{job="frameflow-worker"}`、`frameflow_worker_ready`、队列积压与 Worker
   失败结果，区分指标端点故障和 consumer 未注册的绿壳。
2. 记录 Worker 容器 ID、镜像 Digest、退出码和最近 15 分钟结构化日志。
3. 检查 RabbitMQ/MinIO/API 连通性；禁止在原因不明时重复消费毒消息。
4. 若 DLQ 增长，保留消息元数据但不得把 `ANALYSIS_ERROR` 改写成视频不合格。
5. 恢复同一 Digest 或回滚，确认 scrape `up=1`、`frameflow_worker_ready=1`、消费
   速率恢复、积压下降。
