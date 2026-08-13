# 安全基线

## 应用安全

- Spring Security 6 + RS256 JWT + BCrypt；MVP 由应用 Security Filter 校验，微服务阶段的业务服务仍不能只信任 Gateway 的鉴权结果。
- 所有资源按 Team/Project 归属校验，CLIENT 不能修改内部任务。
- OpenAPI 显式声明的高风险写接口必须使用 Idempotency-Key；M01 以 PostgreSQL 幂等记录为事实源，M05 Redis 只作加速。并发更新使用 version/If-Match。
- 所有响应返回 `X-Request-Id`；错误 body 的 `requestId` 必须与 header 相同。
- 上传校验大小、类型、对象键和内容元数据；下载使用短期预签名 URL。
- 错误不返回堆栈、SQL、服务地址或 Secret；日志按字段脱敏。

## 服务与消息安全

- 内部服务使用明确身份和最小权限；Token 不传播到不必要的下游。
- RabbitMQ/Kafka 权限按 vhost/topic/consumer 最小化；人工重放必须审计。
- AI Provider 只接收最小且脱敏的数据；Prompt、模型、成本和外部请求 ID 可追溯。
- 不把 AI suggestion 直接写入权限、交付锁定或核心状态机。

## 容器与 Kubernetes

- Secret 通过环境变量、Secret Store 或 Kubernetes Secret 注入，不写入镜像、Helm values 或 Git。
- ServiceAccount、Kubernetes RBAC、NetworkPolicy 最小化。
- 容器使用非 root、固定/可追溯镜像 tag、securityContext 和资源限制。
- 镜像构建后进行依赖和漏洞扫描；日志只输出 stdout/stderr。
- readiness 失败时停止接收流量，不能用 liveness 代替业务依赖检查。

## Istio

- 服务间启用 mTLS；AuthorizationPolicy 只允许需要的工作负载访问。
- Istio 策略不能替代领域 RBAC；两层都必须测试。
- 灰度配置必须有回滚和故障注入记录。

## 数据与研发隔离

- 不读取或复用 `jcm_media_api` 配置、密钥、数据库、消息或测试环境。
- 不提交 `.env`、Token、API Key、真实客户数据、真实素材或完整预签名 URL。
- 本地压测、OOM 和故障实验不能写成生产事故或生产容量。
