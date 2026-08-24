# Runbook: FrameFlowAppDown

1. 先确认 Prometheus 自身与 Compose 网络，区分“抓取链路断”与“应用真挂”。
2. `docker compose ... ps app`，记录容器 ID、状态、镜像 Digest 和最近退出码。
3. 在 Compose 内网请求 `http://app:18080/actuator/health/readiness`。
4. 用 Loki 按 `compose_service="app"`、`level="ERROR"` 检索同一时间窗。
5. 若是错误版本，按 F9 Digest 记录回滚；若是依赖故障，转对应依赖 Runbook。
6. 恢复后确认告警 resolved，并从模板生成时间线复盘。

不要先重启再取证；否则会丢失退出码、原始日志和故障窗口。
