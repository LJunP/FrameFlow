# Istio Runbook

> 目标：在本地 Kubernetes 中验证 v1/v2 灰度、mTLS、AuthorizationPolicy、故障注入和回滚。

## 1. 检查安装和注入

```bash
istioctl version
kubectl get pods -n istio-system
kubectl label namespace frameflow istio-injection=enabled --overwrite
kubectl get pod -n frameflow -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[*].name}{"\n"}{end}'
```

确认业务 Pod 存在 sidecar；未注入的 Pod 不得参与 mTLS 实验。

## 2. 检查流量配置

```bash
kubectl apply -f deploy/istio/
istioctl analyze -n frameflow
kubectl get virtualservice,destinationrule,authorizationpolicy,peerauthentication -n frameflow
istioctl proxy-config routes <pod> -n frameflow
istioctl proxy-config clusters <pod> -n frameflow
```

## 3. 灰度验证

- 默认流量：约 90% 到 v1、10% 到 v2；
- `x-canary: true`：固定到 v2；
- 记录请求总数、版本比例、错误率和延迟；
- v2 失败时将权重切回 100% v1。

```bash
kubectl apply -f deploy/istio/virtual-service-90-10.yaml
kubectl apply -f deploy/istio/virtual-service-100-v1.yaml
```

## 4. mTLS 与授权

```bash
kubectl get peerauthentication -n frameflow
istioctl authn tls-check <pod>.frameflow
kubectl get authorizationpolicy -n frameflow
```

验证：受信任工作负载调用成功；未授权 ServiceAccount 被拒绝；应用业务 RBAC 仍由 Spring Security 负责。

## 5. 故障注入与回滚

- 只对 v2 注入延迟/5xx；
- 观察 Gateway、Envoy、应用日志、Trace 和 Prometheus 指标；
- 不用无限重试掩盖故障；
- 恢复前先切回 v1，再移除故障配置；
- 保存 YAML、命令、状态和限制。

## 6. 常见问题

| 现象 | 检查 |
|---|---|
| 503 | VirtualService、Endpoints、sidecar、readiness |
| mTLS 失败 | PeerAuthentication、注入、端口协议 |
| 灰度比例异常 | 请求数量、DestinationRule subset、缓存/连接复用 |
| Policy 拒绝 | ServiceAccount、namespace、selector |
| Trace 断链 | `traceparent` 透传、采样和 Collector |
