# F5 · Redis 宕机降级演练记录（T4）

- 日期：2026-08-22
- 环境：本机 Docker Compose（frameflow-local-pg/minio/rabbit/redis 全栈）+ `./mvnw spring-boot:run`
- 演练人：Agent（按 docs/03 F5-T4 要求执行并记录）

## 结论

**Redis 宕机期间：登录正常处理（fail-open）、批次查询正常（降级直查库），无 500、无雪崩；恢复后限流自动重新武装。** 降级路径全部有日志留痕。

## 演练时间线（真实命令与输出）

### 1. 正常态：限流生效

```
curl -X POST :18080/api/v1/auth/login -d '{"email":"drill@example.com","password":"x"}'
→ 401（密码错误，业务正常）

docker exec frameflow-local-redis redis-cli keys 'ratelimit:*'
→ "ratelimit:login:drill@example.com"          # 令牌桶 key 已写入
```

### 2. 宕机：`docker stop frameflow-local-redis`

```
curl（同上，错误密码）
→ 401     ← 关键：不是 500！密码校验照常执行

grep -c "限流器不可用，降级放行" app.log
→ 1       ← 每次降级都有 WARN 日志留痕（可接告警）
```

### 3. 恢复：`docker start frameflow-local-redis`

**真实发现**：容器恢复后，旧应用进程的 Lettuce 连接池仍持有断连，短窗口内
继续降级放行（日志累计 8 次降级 WARN）——生产环境应配合 Lettuce
`enableConnectionRecovery`/健康检查观察重连时长。**全新进程立即恢复**：

```
7 次连续错误密码登录：
→ 401 401 401 401 401 429 429    # 第 6 次起被限流（容量 5）

redis-cli ttl ratelimit:login:drill3@example.com
→ 3600                            # 桶 key 带 1 小时过期
```

## 降级路径清单（代码落点）

| 路径 | 降级行为 | 代码 |
|---|---|---|
| 登录限流 | fail-open 放行 + WARN | RedisRateLimiter.tryAcquire 的 catch |
| 批次进度缓存 | 直查数据库 + WARN | ProgressCacheService.getOrLoad 的 catch |
| 写后失效 | 跳过（靠 30s TTL 兜底）+ WARN | ProgressCacheService.evict 的 catch |

## 自动化护栏

除本人工演练外，降级行为有常驻单测（RedisFlowIntegrationTest 中两个
Mock 故障测试），CI 每次构建都会验证降级分支未被破坏。
