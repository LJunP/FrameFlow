# Kubernetes Runbook

> 目标：在 kind/minikube 中完成可重复部署、排障、滚动发布和回滚。不是生产集群操作手册。

## 1. 前置检查

```bash
java -version
mvn -version
docker version
kubectl version --client
helm version
kind version
```

确认使用本地镜像、非真实 Secret、隔离 namespace，并记录版本。

## 2. 基础排查顺序

```bash
kubectl get ns
kubectl get pods -n frameflow
kubectl get svc -n frameflow
kubectl get events -n frameflow --sort-by=.lastTimestamp
kubectl describe pod <pod> -n frameflow
kubectl logs <pod> -n frameflow --all-containers
kubectl logs <pod> -n frameflow --previous
```

## 3. 发布与回滚

```bash
helm upgrade --install frameflow ./deploy/helm/frameflow -n frameflow --create-namespace
kubectl rollout status deployment/<name> -n frameflow
kubectl rollout history deployment/<name> -n frameflow
kubectl rollout undo deployment/<name> -n frameflow
```

发布前确认镜像 tag、迁移版本、ConfigMap/Secret、探针和资源限制；发布后确认 readiness、错误率、日志和关键 API。

## 4. 常见故障

| 现象 | 首要检查 | 处理方向 |
|---|---|---|
| `Pending` | `describe`、资源和调度事件 | 调整 requests/节点资源 |
| `CrashLoopBackOff` | 当前/上一轮日志、配置 | 修配置或回滚 |
| `OOMKilled` | limits、堆配置、JFR/GC | 限制并发、定位内存、调整资源 |
| readiness 失败 | `/readiness`、依赖连接 | 修复依赖或保持不就绪 |
| Service 无响应 | selector、端口、Endpoints | 修标签/端口/网络策略 |
| rollout 卡住 | ReplicaSet、事件、探针 | 停止、回滚、补回归测试 |
| HPA 无效 | metrics-server、指标和 requests | 记录环境限制，不伪造扩容结果 |

## 5. 安全要求

- ServiceAccount 最小权限；
- Secret 不写入镜像、Helm values 或日志；
- Pod 使用非 root、只读文件系统（适用时）；
- NetworkPolicy 只开放必要服务；
- 数据库和消息服务的持久化/备份边界必须写清。

## 6. 证据

每次部署保存 Helm values、镜像 tag、kubectl 输出、日志、故障现象、恢复和回滚命令，并在 evidence index 建立引用。
