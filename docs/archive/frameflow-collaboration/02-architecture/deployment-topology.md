# 部署拓扑

## 1. local-monolith

```text
FrameFlow App → PostgreSQL
```

用途：P0～M4 的最小开发和测试。应用、数据库通过 Compose 运行；不启动 Redis、RabbitMQ、Kubernetes 或 Nacos。

## 2. local-worker

```text
FrameFlow App → PostgreSQL
             ↘ Redis / MinIO / RabbitMQ → AI Worker
```

用途：M4-B 起加入 MinIO，M6～M8 形成 backend 闭环，M08-F 再形成产品 MVP Gate。媒体文件直传 MinIO，业务服务只保存元数据和对象键；Redis 只在可选 M05 hardening/engineering-lab 中加入，不是该拓扑的 MVP 必需依赖。

## 3. local-microservices

```text
Client → Spring Cloud Gateway → identity-service → MySQL
                         ↘ project-service → PostgreSQL
                         ↘ asset-workflow-service → PostgreSQL/MinIO
                         ↘ RabbitMQ/Kafka/Redis
                         Nacos：服务发现与非敏感配置
```

用途：M13～M15 的服务边界、Feign、超时、熔断和 Trace 训练。每个服务独立进程和迁移目录。

## 4. local-k8s

```text
Nginx Ingress → Spring Cloud Gateway → Kubernetes Service → Pods
                                                    ↘ PostgreSQL/Redis/MinIO/RabbitMQ
```

用途：M16 的镜像、Helm、探针、资源、滚动发布、HPA、排障和回滚。Kubernetes Service DNS 负责服务发现，ConfigMap/Secret 负责配置注入。

## 5. local-istio

```text
Istio Gateway → Spring Cloud Gateway → Istio Sidecar → Service Pods
                                                   ↘ VirtualService/DestinationRule
                                                   ↘ mTLS/AuthorizationPolicy
```

用途：M16-G 的 v1/v2 灰度、mTLS、策略和故障注入。基础 Nginx Ingress 不与 Istio Gateway 同时承担同一入口流量。

## 6. 依赖边界

- 本地学习环境中的 PostgreSQL、Redis、MinIO、RabbitMQ、Kafka、MySQL 使用隔离实例和非真实凭据。
- 不把有状态业务依赖随意部署为无持久化的生产替代品；K8s 阶段重点验证应用发布和故障排查。
- 数据库迁移在应用发布前执行，失败必须阻止就绪，不在启动脚本中无限等待。
- 所有外部端点、凭据和资源限制通过环境配置注入，不写入镜像。
