# 本地开发约定

## 1. 通用规则

- 真实配置不提交；只提交 `.env.example` 和脱敏示例。
- 所有测试使用测试 profile，不访问旧项目或外部生产服务。
- 每种运行模式使用独立 Compose project/namespace 和非真实凭据。
- 本地性能、OOM、K8s 和 Istio 结果只能作为隔离实验，不宣传为生产结论。
- 启动、停止、清理、测试和证据命令必须写入 README 并实际验证。

## 2. 运行模式

| 模式 | 依赖 | 用途 | 阶段 |
|---|---|---|---|
| `local-monolith` | PostgreSQL | 单体核心开发 | P0～M4 |
| `local-worker` | PostgreSQL、Redis、MinIO、RabbitMQ | AI/审核/交付闭环 | M5～M8 |
| `local-microservices` | Gateway、Nacos、MySQL、PostgreSQL、Redis、RabbitMQ | 服务治理训练 | M13～M15 |
| `local-k8s` | kind/minikube、Helm、镜像 | K8s 发布和排障 | M16 |
| `local-istio` | Kubernetes、Istio | 灰度、mTLS、授权 | M16-G |

P0 本地基座已可运行：

```text
docker compose -f docker-compose.local.yml up -d --wait
./mvnw -B clean verify
java -jar frameflow-app/target/frameflow-app-0.1.0-SNAPSHOT.jar
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/readiness
docker compose -f docker-compose.local.yml stop
```

应用默认监听 `127.0.0.1:8080`，数据库默认绑定 `127.0.0.1:54329`；真实 `.env` 不提交，默认值只适用于本地隔离环境。

## 3. 每种模式必须记录

```text
前置工具和版本
启动命令
配置来源与 Secret 边界
依赖健康检查
业务/测试命令
停止和清理命令
常见故障和 Runbook
证据输出路径
```

依赖启动不能使用固定 `sleep` 代替健康检查；应用 readiness 必须反映真正的接流量条件。
