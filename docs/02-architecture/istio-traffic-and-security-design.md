# Istio 流量与安全设计

> 状态：PLAN。M16-G 的受限求职实验，不属于 MVP 产品功能。

## 1. 实验目标

用 `asset-workflow-service` 的 v1/v2 版本验证：

- 按权重或请求 Header 灰度；
- DestinationRule 版本子集；
- mTLS 服务间加密和身份认证；
- AuthorizationPolicy 访问控制；
- 下游故障注入、指标/Trace 观察和快速回滚。

## 2. 资源设计

- `Deployment/asset-workflow-v1` 与 `Deployment/asset-workflow-v2` 使用同一 Service。
- Pod 标签 `app=asset-workflow`、`version=v1|v2`。
- `DestinationRule` 定义 v1/v2 subsets。
- `VirtualService` 默认 90% 到 v1、10% 到 v2；带 `x-canary: true` 的请求固定到 v2。
- `PeerAuthentication` 在实验 namespace 开启 STRICT mTLS。
- `AuthorizationPolicy` 只允许 Gateway 和受信任的 Worker 调用指定接口。

## 3. 职责分层

| 能力 | 应用层 | Istio |
|---|---|---|
| 业务鉴权 | Spring Security/RBAC | 工作负载身份和网络访问 |
| 业务重试 | Resilience4j，有限且幂等 | 默认关闭或只保留一层基础重试 |
| 路由 | Gateway 业务路由 | 版本、权重、灰度路由 |
| 加密 | HTTPS 入口 | 服务间 mTLS |
| 指标/Trace | Actuator/OTel | 代理流量指标和透传 |

禁止应用和网格同时配置无限重试；任何重试必须有超时、预算和幂等依据。

## 4. 验收场景

1. 发送固定数量无 Header 请求，观察约 90/10 的版本比例；
2. 发送 `x-canary: true`，确认全部到 v2；
3. 查询 `istioctl proxy-config` 或等价证据确认路由；
4. 启用 STRICT mTLS，验证非网格明文请求被拒绝、网格内请求成功；
5. 用 AuthorizationPolicy 拒绝未授权工作负载；
6. 对 v2 注入延迟或 5xx，观察错误率和 Trace；
7. 权重切回 100% v1，验证业务恢复；
8. 保存 YAML、命令、原始输出和限制说明。

## 5. 不做的内容

- 不做多集群、跨地域、生产证书体系；
- 不将 Istio 当作业务权限系统；
- 不为了展示 Mesh 而拆更多服务；
- 不把本地实验数字表述为生产 SLO。
