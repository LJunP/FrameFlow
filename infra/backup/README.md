# F10 备份、校验与隔离恢复

本目录覆盖 PostgreSQL（业务唯一事实源）和 MinIO（媒体字节）的备份/恢复。
所有脚本默认 dry-run；写入备份或恢复都必须显式 `--execute`。现有目标覆盖恢复
还要求逐字 typed confirmation，production 另需人工门禁参数。

## 产物与边界

| 脚本 | 默认行为 | 执行产物/影响 |
| --- | --- | --- |
| `postgres-backup.sh` | dry-run | custom-format dump、manifest、SHA-256 |
| `postgres-restore-drill.sh` | dry-run | 随机隔离 PG，无宿主端口；验证后强制清理 |
| `postgres-restore-target.sh` | dry-run | 覆盖显式目标 DB；高风险、typed confirmation |
| `minio-backup.sh` | dry-run | 当前对象逻辑镜像、对象清单、SHA-256 |
| `minio-restore-drill.sh` | dry-run | 随机隔离 MinIO；回读逐字节校验后强制清理 |
| `minio-restore-target.sh` | dry-run | 覆盖显式目标 bucket；高风险、typed confirmation |
| `verify-backup-set.sh` | 只读 | 校验 manifest、文件集合和 SHA-256 |

MinIO 的逻辑镜像覆盖“当前对象版本”，不保存历史版本链。production 若启用对象
版本化，仍需独立站点复制/对象锁策略；本地逻辑恢复 PASS 不等于跨机灾备成立。

目标覆盖恢复还有一个不可跳过的维护前置条件：操作者必须先停止同一 Compose
project 的 `app`、`worker`、`web`，并在宿主 Nginx/负载均衡层阻断新流量。两个
target restore 脚本会用容器 project label 和运行状态复核前三个服务；任一仍运行
立即拒绝。脚本不会在失败后自动恢复流量，避免把半恢复状态重新暴露。完成恢复、
核对数据库/对象与队列边界后，再由操作者启动服务并执行 F9 Smoke。production 的
maintenance-window 与 `--allow-production` 门禁仍然同时有效。

## 完整性模型

备份脚本先完成 `manifest.json`，再枚举备份目录中除 `SHA256SUMS` 自身以外的
全部 regular non-symlink files，并为这个精确集合生成 SHA-256。PostgreSQL 集合
至少包含 `dump.custom`、`restore-list.txt`、`manifest.json`；MinIO 集合包含
`manifest.json`、`object-inventory.jsonl` 与 `objects/` 下的全部对象。

`verify-backup-set.sh` 会拒绝空 checksum、重复/越界/非规范路径、symlink、特殊文件、
漏列文件、磁盘上缺失的条目和摘要不一致。SHA-256 用于发现不完整或意外损坏，
不等于抗恶意篡改签名；production 仍需不可变存储、独立信任域或签名策略。

## 定时任务安装边界

`frameflow-backup@.service` 固定以非 root `frameflow` 身份运行，并显式加入
root-equivalent 的 `docker` supplementary group。它只从稳定的
`/opt/frameflow/ops/current` 读取脚本；该路径由 F9
`install-ops-bundle.sh` 从 image manifest 指向的 Git commit 提取 tracked
`infra/` bytes、生成精确 SHA-256 清单后原子安装。不要把临时 checkout 路径直接
写进 unit。

安装运行文件时使用统一权限模型，父目录保持 `root:frameflow 0750`：

```bash
sudo install -o root -g frameflow -m 0640 frameflow.env \
  /etc/frameflow/production/frameflow.env
sudo install -o root -g frameflow -m 0640 infra/backup/config/backup.conf.example \
  /etc/frameflow/production/backup.conf
```

`frameflow.env` 含 Secret；`backup.conf` 本身不含 Secret，但两者都必须同时满足
`root:frameflow 0640`，否则调度入口 fail-closed。启用 timer 前，所有者还要确认
`/var/backups/frameflow-<env>` 为 `root:frameflow 0770`，安装 unit/timer，先执行以下
非写入权限/路径 preflight，再运行 service 并验证两类 backup set，最后启用 timer：

```bash
sudo -u frameflow -g frameflow \
  /opt/frameflow/ops/current/infra/backup/run-scheduled-backups.sh \
  --environment production --config /etc/frameflow/production/backup.conf --validate-only
sudo systemctl start frameflow-backup@production.service
```

Docker group 等同宿主 root 权限，因此该账号不能复用为容器内应用身份。

## PostgreSQL 本地例子

```bash
bash infra/backup/postgres-backup.sh \
  --environment local \
  --compose-file "$PWD/infra/local/docker-compose.yml" \
  --env-file "$PWD/infra/local/env.example" \
  --project-name frameflow-local \
  --service postgres \
  --database frameflow_local \
  --username frameflow_local \
  --output-dir /tmp/frameflow-backups/local \
  --execute

bash infra/backup/postgres-restore-drill.sh \
  --source-environment local \
  --backup-dir /tmp/frameflow-backups/local/postgres-local-时间戳 \
  --evidence-dir /tmp/frameflow-restore-evidence/local \
  --execute
```

## MinIO 本地例子

MinIO 备份复用 F9 `minio-init` 服务的镜像与已注入凭据，脚本不把 Secret 读回
宿主 shell 或打印到日志：

```bash
bash infra/backup/minio-backup.sh \
  --environment local \
  --compose-file "$PWD/infra/local/docker-compose.yml" \
  --env-file "$PWD/infra/local/env.example" \
  --project-name frameflow-local \
  --client-service minio-init \
  --bucket frameflow-media-local \
  --output-dir /tmp/frameflow-backups/local \
  --execute
```

## RPO / RTO 初始目标（需真实流量校准）

- PostgreSQL：每 6 小时 full backup，目标 RPO ≤ 6h；隔离恢复目标 RTO ≤ 60m。
- MinIO 当前对象：每日逻辑镜像，目标 RPO ≤ 24h；隔离恢复目标 RTO ≤ 120m。
- 保留建议：本地/dev 7 天，staging 14 天，production 30 天；删除策略必须单独
  审批，本目录不自动删除历史备份。

以上是工程目标，不是已测 production 事实。只有带真实数据规模、另一实例、耗时、
校验和与责任人签字的恢复证据才能关闭生产恢复门禁。
