# Runbook: 依赖不可用

## PostgreSQL

1. 立即停止会增加写入压力的批量动作；记录 `pg_up`、连接池与应用错误率。
2. 检查容器、磁盘、连接数和最近迁移；不要在未确认备份前做破坏性修复。
3. 需恢复时先走 `infra/backup/postgres-restore-drill.sh` 的隔离恢复验证。
4. 真实目标恢复必须使用显式环境、项目、服务和 typed confirmation。

## Redis

1. 确认业务是否按设计降级直查数据库、登录限流是否 fail-open。
2. 监控数据库负载，必要时降低流量；Redis 不是业务事实源，不从它恢复业务状态。
3. 恢复后等待 TTL/Cache-Aside 自然重建，不把旧缓存当权威写回数据库。
4. 当前监控用户是 runtime ACL；Redis 容器重启/重建后先重新执行
   `provision-exporter-credentials.sh`，再运行 `observability-stack.sh --action up`。
   后者会 fail-closed 校验 `frameflow_monitor`，不能临时改用业务管理员密码冒充修复。

## RabbitMQ

1. 确认 `up{job="rabbitmq"}` 与 `up{job="rabbitmq-detailed"}`，区分节点不可用和
   detailed family 抓取失败。
2. 在 RabbitMQ 容器内确认 `rabbitmq_prometheus` 已启用，目标 vhost 仍存在；
   指标端口 `15692` 只在 Compose 内网 expose，不应映射到公网。
3. 检查 broker 磁盘/内存水位、连接与 channel；不要通过 purge 队列制造恢复假象。
