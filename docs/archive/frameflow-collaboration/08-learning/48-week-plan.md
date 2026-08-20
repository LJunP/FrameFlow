# 48 周学习与开发路线（权威版）

> 状态：PLAN（学习参考）。本文是 FrameFlow 的**学习路线**，不是开发输入；开发调度以 `docs/05-engineering/tasks/*.json` 为准。
> 技术事实以 PRD、ADR、概要设计和详细设计为准。MVP 从 P0 起使用 PostgreSQL 唯一事务主库；MySQL 只在 M13（engineering-lab）的 identity-service 落地。

## 1. 学习闭环（每站固定循环）

```text
业务问题 → 最小原理 → 最小实现 → 自动化测试 → 边界/故障实验 → 日志/指标观察 → 修复复盘 → 口述解释
```

## 2. 48 周一览表

| 周次 | 主题 | 对应项目阶段 | 技术 | 通关标志 |
|---|---|---|---|---|
| 1～3 | Java 语言基础 | 全部 | 语法、OOP、集合、泛型、异常、Stream | 能独立写 200 行带泛型/异常/Stream 的类 |
| 4～5 | 计算机与网络地基 | P0 起 | Linux、HTTP、TCP、DNS、端口 | 用 curl 调通一个接口并解释状态码 |
| 6 | Git + Maven | P0 | 分支、提交规范、多模块、依赖 | 完成一次含回滚的提交流程 |
| 7～9 | PostgreSQL + SQL | P0～M4 | SQL、事务、索引、表设计、乐观锁 | 能给 users/teams/projects 写迁移 SQL |
| 10～12 | Spring Boot + MVC | P0～M1 | IoC/DI、自动配置、REST、校验、异常 | 独立写一个带校验和错误码的 REST 模块 |
| 13～14 | Spring Security + JWT | M1 | 认证、授权、RBAC、BCrypt、Token | 实现登录→发 Token→接口校验→角色拒绝 |
| 15～16 | MyBatis-Plus + Flyway | M1～M3 | Mapper、XML、迁移版本管理 | 完成一个模块的持久层+迁移 |
| 17～18 | 并发与多线程 | M3、M6、M12 | 线程池、CompletableFuture、乐观锁、JMM | 解释两个请求同时改一条数据的后果 |
| 19 | Docker + Compose | P0、M4 | 镜像、容器、卷、健康检查 | 用 Compose 起 PostgreSQL 并接上应用 |
| 20 | Redis | M5 | Cache-Aside、TTL、限流、PostgreSQL 幂等加速与安全回源 | Redis 命中与故障回源都保持同一幂等业务结果 |
| 21 | MinIO | M4-B | 对象存储、预签名 URL、补偿 | 无权限用户拿不到预签名 URL |
| 22～23 | RabbitMQ | M6 | Exchange、ACK、重试、DLQ、幂等 | 消费者停机消息不丢、毒消息进 DLQ |
| 24～25 | DDD | M9 | 限界上下文、聚合、值对象、Repository | 画出 Asset 聚合图并说出 8 条不变量 |
| 26～27 | Kafka + Outbox | M11 | Topic、offset、Outbox、DLT、重放 | 事务内 Outbox + 补发演示 |
| 28～29 | 测试体系 | 贯穿（Testcontainers 随首次引入）、M12-B 强化 | JUnit5、Mockito、MockMvc、Testcontainers、ArchUnit | 关键规则测试稳定、基础设施集成测试可复现 |
| 30～31 | 性能与 JVM | M12 | k6、EXPLAIN、JFR、jcmd、jstack、GC | 三个故障实验（堆积/OOM/死锁）有复盘 |
| 32 | MySQL 差异 | M13（lab） | MySQL vs PostgreSQL、数据所有权 | 能解释 identity-service 为什么用 MySQL |
| 33～35 | Spring Cloud | M13 | Gateway、Nacos、Feign、LoadBalancer | 四个服务注册发现 + 路由 |
| 36～37 | 服务治理 | M14 | Resilience4j、超时、熔断、降级 | 下游变慢→超时→熔断→降级故障链 |
| 38～39 | 可观测性 | M15 | OTel、Prometheus、Grafana、ELK | 一个 traceId 找回完整调用链 |
| 40～42 | Kubernetes | M16 | Pod、Deployment、Probe、HPA、Helm、回滚 | CrashLoopBackOff/OOM 排障 + rollout undo |
| 43～44 | Istio | M16-G | Sidecar、VirtualService、mTLS、策略 | 90/10 灰度 + 回滚证据 |
| 45～48 | AI 辅助研发与求职交付 | M17 | 提示词工程、证据整理、面试表达 | evidence-index 完整、演示稿可讲 |

## 3. 与项目阶段的对应关系

```text
第 1～19 周  → P0～M4（Java/Spring/数据/Docker，产品主线地基）
第 20～23 周 → M5～M8（Redis/MinIO/RabbitMQ/AI/审核交付，产品主线闭环）
第 24～31 周 → M9～M12（DDD/Kafka/测试/压测/JVM，工程实验）
第 32～39 周 → M13～M15（MySQL/Spring Cloud/治理/可观测，工程实验）
第 40～44 周 → M16～M16-G（K8s/Istio，工程实验）
第 45～48 周 → M17（求职交付）
```

## 4. 原则

- 学习路线不覆盖架构 ADR；技术引入以业务问题和阶段门禁为准。
- 每站学习结果必须落进 FrameFlow 对应模块，不允许脱离项目的空学。
- 历史存档（含旧版完整路线和旧项目语境）见 `legacy/`，不作为权威来源。
