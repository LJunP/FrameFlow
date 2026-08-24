# local：完整本地栈

本目录用源码构建 Java、Worker、Web 三个非 root 镜像，并启动 PostgreSQL、Redis、
RabbitMQ、MinIO。所有宿主端口只绑定 `127.0.0.1`，默认值仅用于本机隔离开发。

```bash
cp infra/local/env.example infra/local/.env
infra/scripts/check-env-isolation.sh --environment local --env-file infra/local/.env
docker compose --env-file infra/local/.env -f infra/local/docker-compose.yml up -d --build
infra/scripts/smoke-test.sh --environment local \
  --compose-file infra/local/docker-compose.yml --env-file infra/local/.env \
  --base-url http://127.0.0.1:3000
docker compose --env-file infra/local/.env -f infra/local/docker-compose.yml down
```

`down` 不删除命名卷；只有明确要丢弃本地测试数据时才由所有者另行执行
`down --volumes`。Compose 默认不启用任何真实 AI Provider Key，语义调用会按既有
fail-closed 规则形成待复核错误，而不会静默 Fake。
