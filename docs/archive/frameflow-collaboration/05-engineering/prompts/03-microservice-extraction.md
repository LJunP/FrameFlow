# Prompt 03：微服务拆分与 Spring Cloud 治理

只有模块边界、业务 E2E、数据所有权、契约和回滚门禁通过后，执行 M13～M15。

## 固定服务

```text
gateway-service
identity-service       → MySQL
project-service        → PostgreSQL
asset-workflow-service → PostgreSQL/MinIO
```

## 必须

- 禁止共享业务表、跨库外键、双写和跨数据库事务。
- 每个服务独立迁移、契约、配置、日志和健康检查。
- 使用 Gateway、Nacos（仅 Compose）、OpenFeign、LoadBalancer、Resilience4j。
- 演示下游变慢 → 超时 → 有限重试 → 熔断 → 降级 → Trace/日志/指标定位。
- 使用 OpenTelemetry、Prometheus/Grafana、结构化日志、Fluent Bit、Elasticsearch/Kibana。
- 记录服务拆分理由、数据迁移、切流、兼容窗口和回滚。

不要继续拆十几个服务；没有独立部署、扩缩容或故障隔离理由就停止拆分。
