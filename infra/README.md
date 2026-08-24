# FrameFlow Select 部署工程

这里是 F9 的本地可验证部署产物，不是已发生的生产发布。

| 环境 | Compose | 入口 | 数据隔离 |
| --- | --- | --- | --- |
| local | `local/docker-compose.yml` | 127.0.0.1:3000 / 9000 | `frameflow-local` project |
| dev | `dev/docker-compose.yml` | 宿主 Nginx → 127.0.0.1:13000 / 19000 | `frameflow-dev` project |
| staging | `staging/docker-compose.yml` | 宿主 Nginx → 127.0.0.1:23000 / 29000 | `frameflow-staging` project |
| production | `production/docker-compose.yml` | 独立 VPS Nginx → 33000 / 39000 | `frameflow-production` project |

远程三环境 include 同一份 `scripts/remote-compose.yml`，保证服务拓扑不漂移；差异只能
来自经 fail-closed 校验的 env 文件。每套环境仍拥有独立网络、命名卷、数据库、Redis、
RabbitMQ VHost、MinIO Bucket、JWT key、域名、端口、日志和备份。

关键入口：

- `scripts/validate-deploy-config.sh`：离线检查模板、Compose、`latest`、隔离矩阵；
- `scripts/generate-image-manifest.sh` / `validate-image-manifest.sh`：SHA、版本、tag、Digest；
- `scripts/promote-release.sh` / `rollback.sh`：默认 dry-run、显式确认、Smoke 后切换指针；
- `scripts/install-ops-bundle.sh`：从 manifest Git commit 安装 exact tracked `infra/`
  到 `/opt/frameflow/ops/current`，供备份 unit 使用；
- `nginx/`：HTTP ACME bootstrap、HTTPS、安全头、媒体域名与续期 timer；
- `docs/guides/F9-源码导读.md`：从镜像到人工部署门禁的完整阅读/操作路线。

真实 `.env`、JWT 私钥、Provider Key、客户媒体均不得进入仓库。真实 Provider、VPS、
SSH、DNS、证书请求、生产发布与生产恢复仍是所有者之后亲手执行的外部门禁。
