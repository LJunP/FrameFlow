# dev：持续联调环境模板

dev 与 staging 可在同一非生产 VPS，但必须使用不同 Compose project、凭据、卷、
RabbitMQ VHost、MinIO Bucket 和 loopback 端口。宿主 Nginx 只反代
`127.0.0.1:13000/19000`，数据服务没有宿主端口。

首次人工部署顺序见 `docs/guides/F9-源码导读.md`。任何 `CHANGE_ME`、示例域名、
全零 Digest 都会被隔离校验拒绝；真实 `.env` 放
`/etc/frameflow/dev/frameflow.env`，安装为 `root:frameflow 0640`，不进仓库。
