# Prompt 04：Docker、Helm 与 Kubernetes

在微服务可独立构建和运行后执行 M16。

## 必须

- 多阶段、非 root、无 Secret 的 Dockerfile；健康检查和 stdout 日志。
- kind/minikube + Helm；Namespace、Deployment、Service、ConfigMap、Secret、ServiceAccount、securityContext、NetworkPolicy、PDB。
- readiness/liveness/startup probe、requests/limits、滚动更新、rollout history/undo、HPA（适用时）。
- 使用 Kubernetes Service DNS；Nginx Ingress 负责基础入口，Gateway 负责应用路由。
- 演练 Pending、CrashLoopBackOff、OOMKilled、readiness 失败、错误发布和回滚。

## 证据

保存镜像 tag、Helm values、kubectl get/describe/logs/events、rollout 和回滚原始输出；诚实记录本地集群限制。
