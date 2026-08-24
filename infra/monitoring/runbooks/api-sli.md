# Runbook: API 错误率或延迟超阈值

1. 按 route/status 拆分 `http_server_requests_seconds_*`，避免被健康检查稀释。
2. 用 `request_id` 关联 Java JSON 日志；检查 DB 连接池、Redis 降级与 MQ confirm。
3. 对照发布 Digest 与告警开始时间；发布相关则执行 F9 回滚，不现场改镜像。
4. 恢复后记录真实影响窗口、请求量、5xx 比例与 P95，不用单次峰值替代区间数据。
