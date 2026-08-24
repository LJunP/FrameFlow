# F10 可观测性叠加栈

本目录提供 Prometheus、Alertmanager、Grafana、Loki、Grafana Alloy 及依赖导出器。
它是 F9 环境 Compose 的叠加文件，不复制应用服务，也不依赖 `container_name`。

## 边界

- `docker-compose.observability.yml` 必须与一个
  `infra/{local,dev,staging,production}/docker-compose.yml` 用相同的
  `COMPOSE_PROJECT_NAME` 一起启动，因而自动加入同一隔离网络。
- Prometheus 只通过 Compose 内网抓取 `app:18080/actuator/prometheus` 与
  `worker:9108/metrics`。Nginx 不应把 `/actuator/**` 暴露到公网。
- App/Worker 使用 Docker `syslog` logging driver，把 RFC5424 日志以 non-blocking
  模式推到当前环境独占的宿主 loopback UDP 端口；Alloy 只监听该端口并按固定
  `frameflow-app|worker` tag 接收。监控栈没有 Docker socket、API proxy 或 container
  inspect 权限，因此 Alloy 无法读取业务容器环境变量中的 Secret。Docker dual
  logging cache 保持启用并限制为 5 × 20 MiB，Loki 不可用时仍可用 `docker logs`
  查看近期缓存；UDP/缓冲区在极端拥塞时可能丢日志，告警与审计不能假设零丢失。
  Alloy 本身固定为镜像内 `473:473`，并丢弃全部 Linux capabilities。
- `alert-sink` 位于显式 `synthetic-alert-drill` profile，只能证明合成告警到本地
  接收端的链路，不能当作短信、邮件或企业 IM 已接通；production 启动脚本拒绝它。
- 真实告警接收器配置由 `scripts/render-alertmanager-config.sh` 从一个权限为
  `0600` 的 Secret 文件渲染到不入库的运行目录。渲染动作必须由 root 执行；
  运行配置固定为 Alertmanager 容器身份 `65534:65534` 所有、权限 `0400`。
  `observability-stack.sh` 会在 Compose 运行前复核 owner 与 mode，仓库不保存
  URL 或 Token。
- PostgreSQL/Redis exporter 使用 `scripts/provision-exporter-credentials.sh` 幂等创建
  最小权限用户；RabbitMQ 改用内置 `rabbitmq_prometheus` 插件，不再创建监控用户。
  基础 Compose 只在内网 expose `15692`；Prometheus 分别抓取 `/metrics` 与仅含
  `queue_coarse_metrics`、`queue_consumer_count` 的 `/metrics/detailed`。
  Redis 当前 Compose 未配置 `aclfile`，运行时 ACL 在容器重建后可能丢失；每次
  `observability-stack.sh --action up` 都会检查，缺失即拒绝启动并要求重新 provision。
  这是一项已知运维步骤，不宣称 Redis 监控凭据永久持久化。

## 本地启动（示例）

先完成 F9 的 local 全栈启动。以下运行文件必须放在仓库外并保持 `0600`；
示例中的 `CHANGE_ME_*` 都是占位符，必须在本机替换，不能提交。PostgreSQL 与
Redis exporter 各用新凭据，不能复用业务管理员凭据。

```bash
repo_root="$(pwd -P)"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/frameflow-monitoring.XXXXXX")"
chmod 700 "$runtime_dir"
base_env="$runtime_dir/local.env"
exporter_env="$runtime_dir/exporters.env"
cp "$repo_root/infra/local/env.example" "$base_env"
printf '\nFRAMEFLOW_GRAFANA_ADMIN_PASSWORD=CHANGE_ME_LOCAL_GRAFANA_20_CHARS\n' >> "$base_env"
chmod 600 "$base_env"
# 用编辑器替换 base_env 内全部本地占位值，切勿把该文件复制回仓库。

export FRAMEFLOW_MONITOR_POSTGRES_PASSWORD='CHANGE_ME_MONITOR_PG_20_CHARS'
export FRAMEFLOW_MONITOR_REDIS_PASSWORD='CHANGE_ME_MONITOR_REDIS_20_CHARS'
bash "$repo_root/infra/monitoring/scripts/provision-exporter-credentials.sh" \
  --environment local \
  --compose-file "$repo_root/infra/local/docker-compose.yml" \
  --env-file "$base_env" \
  --project-name frameflow-local \
  --database frameflow_local \
  --postgres-admin frameflow_local \
  --rabbit-vhost /frameflow-local \
  --output-env-file "$exporter_env" \
  --execute
unset FRAMEFLOW_MONITOR_POSTGRES_PASSWORD \
  FRAMEFLOW_MONITOR_REDIS_PASSWORD

bash "$repo_root/infra/monitoring/scripts/observability-stack.sh" \
  --action up \
  --environment local \
  --compose-file "$repo_root/infra/local/docker-compose.yml" \
  --env-file "$base_env" \
  --exporter-env-file "$exporter_env" \
  --project-name frameflow-local \
  --alert-mode synthetic \
  --execute
```

端口只绑定 loopback：Grafana `127.0.0.1:13000`、Prometheus `:19090`、
Alertmanager `:19093`、Loki `:13100`、本地告警接收证据 `:19094`；Alloy syslog
使用 `127.0.0.1:15140/udp`。dev/staging 的所有观测端口也在 env matrix 中强制唯一。

停止时复用相同的仓库外运行文件，只停止观测服务；不要对业务卷执行
`down -v`：

```bash
bash "$repo_root/infra/monitoring/scripts/observability-stack.sh" \
  --action stop \
  --environment local \
  --compose-file "$repo_root/infra/local/docker-compose.yml" \
  --env-file "$base_env" \
  --exporter-env-file "$exporter_env" \
  --project-name frameflow-local \
  --alert-mode synthetic \
  --execute
```

## 静态校验

```bash
bash infra/monitoring/scripts/validate-monitoring.sh
# 若本机 Docker 可用，再校验 Prometheus/Alertmanager 原生配置：
bash infra/monitoring/scripts/validate-monitoring.sh --docker
```

## 本地告警触达验证

1. 启动观测栈。
2. 临时停止 `app`，等待 `FrameFlowAppDown` 的 `for: 2m`。
3. 打开 Alertmanager 或执行
   `curl -fsS http://127.0.0.1:19094/alerts` 查看 sink 收到的 payload。
4. 立即恢复 `app`，确认告警转为 resolved。

这一步是 local/dev 演练证据，不是 production 告警触达门禁。

## 真实告警接收器权限契约

外部 receiver 的原始 webhook 文件与渲染结果是两种不同角色的 Secret：原始文件
保持宿主 root 可读的 `0600`；Alertmanager 运行配置改由容器固定身份
`65534:65534` 独占读取，并设为 `0400`。不要用 `0644`/`0444` 解决容器权限错误。

```bash
secret_file=/etc/frameflow/alertmanager-webhook.secret
runtime_config=/var/lib/frameflow/monitoring/alertmanager.yml
sudo bash "$repo_root/infra/monitoring/scripts/render-alertmanager-config.sh" \
  --environment production \
  --secret-file "$secret_file" \
  --output "$runtime_config"

# 预期 numeric owner/mode：65534:65534 400；命令不读取或打印文件内容。
stat -c '%u:%g %a' "$runtime_config"
```

渲染器拒绝覆盖既有文件；轮换 webhook 时应先生成一个新的精确路径，完成配置
校验后再显式切换运行参数。`observability-stack.sh --alert-mode external` 只接受上述
owner/mode，不会把宿主操作者所有的 `0600` 错当成容器可读。

## Dashboard 与日志字段

Grafana 会自动安装 Prometheus/Loki 数据源及 `FrameFlow Select / Operations`
面板。Java 和 Worker 日志均为单行 JSON，低基数字段（`level`、`service`、
`environment`、`event`）可作 Loki label；`request_id`、`run_id` 只作为日志字段，
避免高基数标签拖垮 Loki。
