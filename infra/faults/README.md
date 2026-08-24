# F10 非生产故障演练 Harness

`run-drill.sh` 支持四类授权内演练：

| scenario | 注入方式 | 数据边界 |
| --- | --- | --- |
| `redis-unavailable` | `docker compose stop redis` 后恢复 | 不删卷、不改 key；应最后执行并重新 provision runtime monitor ACL |
| `db-unavailable` | `docker compose stop postgres` 后恢复 | 不删卷、不执行 SQL |
| `worker-crash` | `docker compose stop worker` 后恢复 | 不 purge/requeue 业务消息 |
| `mq-backlog` | 创建随机、10 分钟 `x-expires` 且正常路径严格删除的专用 drill queue | 不向业务 exchange/queue 发消息，也不会触发业务队列 backlog 告警 |

所有场景默认 dry-run，显式拒绝 production。staging 还需要
`--allow-staging --confirmation DRILL:staging:<project>:<scenario>`。脚本要求
`--env-file`，因此从任意 cwd 执行也不会漏掉 remote Compose 的 required 变量。

示例：

```bash
bash infra/faults/run-drill.sh \
  --scenario redis-unavailable \
  --environment local \
  --compose-file "$PWD/infra/local/docker-compose.yml" \
  --env-file "$PWD/infra/local/env.example" \
  --project-name frameflow-local \
  --health-url http://127.0.0.1:18080/actuator/health \
  --evidence-dir /tmp/frameflow-fault-evidence/local \
  --duration-seconds 10 \
  --execute
```

证据结果名为 `HARNESS_PASS`：只有 PostgreSQL、Redis、RabbitMQ、API HTTP 200、
Worker consumer readiness 以及专用队列清理全部验证通过后才会写出该结果。RabbitMQ
4.3 默认拒绝已弃用的 transient non-exclusive queue，因此演练队列是 durable，但
设置 10 分钟未使用自动过期；正常路径和 trap 仍主动删除。它表示
精确故障被注入、观察、恢复并清理，不表示 production 韧性、告警真实触达或业务
RTO 已证明。`mq-backlog` 只验证隔离 harness；业务队列告警应通过单独的合成告警
门禁验证。Redis 场景会重启 Redis，runtime ACL 可能丢失，因此应放在最后执行并
重新运行 monitoring principal provision。执行后应复制
`docs/templates/F10-事故复盘模板.md`，结合真实指标补全影响和改进行动。
