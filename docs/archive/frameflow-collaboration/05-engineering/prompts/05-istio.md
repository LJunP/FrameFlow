# Prompt 05：Istio 最小服务网格实验

在 Kubernetes 基础发布和回滚通过后执行 M16-G；这不是 MVP 功能。

## 实验

- `asset-workflow-service` v1/v2；
- DestinationRule 定义 subsets；
- VirtualService 默认 90/10，Header 可固定 v2；
- PeerAuthentication STRICT mTLS；
- AuthorizationPolicy 限制 Gateway/Worker；
- 对 v2 注入延迟或 5xx，观察指标、日志、Trace；
- 权重切回 100% v1 并验证恢复。

## 约束

应用层 RBAC 不由 Istio 替代；应用和 Mesh 不同时配置无限重试；只做一个可验证实验，不做多集群或生产证书体系。

必须保存 YAML、istioctl/kubectl 命令、流量比例、mTLS、策略、故障和回滚证据。
