# BOOK-03:系统架构与技术决策

## 1. 目标技术栈(固定)
- Java 后端:Java 21 LTS、Spring Boot 3.x 稳定兼容版、Maven 多模块、模块化单体、Spring MVC、Spring Security(JWT/Refresh Token,RS256)、MyBatis 或 MyBatis-Plus、Bean Validation、Flyway、JUnit、Testcontainers、ArchUnit、OpenAPI。
- PostgreSQL 16:唯一业务事实源。
- Redis:可丢失可重建,不作为最终业务事实;真实用途:接口限流、Quality Profile Cache-Aside、批次摘要/进度热点缓存、TTL、缓存失效、宕机降级、穿透与击穿实验。
- RabbitMQ:Producer、Consumer、ACK、Publisher Confirm、Retry、DLQ、消息幂等、重复消息、积压、失败恢复、Outbox Pattern。
- MinIO / S3 兼容存储:Bucket、Object Key、Presigned URL、Multipart Upload、文件校验、对象版本、权限、生命周期、数据备份。
- Python 与 AI:Python 3.11+、pytest、FFmpeg/ffprobe、OpenCV、ASR、OCR、Embedding、多模态模型 Adapter、离线 Evaluation。
- 前端:Next.js、React、TypeScript、App Router。
- 基础设施:Linux、SSH、Docker、Compose、Nginx、HTTPS、DNS、GitHub Actions、Container Registry、Prometheus、Grafana、结构化日志、Loki 或等价、备份/恢复/回滚。

## 2. 禁止(除非真实测量证据)
Kubernetes、Istio、Kafka、复杂微服务、Service Mesh、大型向量数据库集群、追新的框架大版本迁移。

## 3. 关键架构决策(默认)
- 模块化单体,不拆微服务;
- PostgreSQL 唯一事实源;Redis 永远可丢失、可重建、不充当最终事实;
- 消息必须幂等(重复投递安全),失败进 DLQ 且可重放;
- 对象字节与元数据分离:MinIO/S3 存字节,PostgreSQL 存元数据,上传用 Presigned URL/Multipart 与文件校验;
- Worker 与主应用解耦:分析任务异步,结果按契约幂等入库;
- 安全:JWT 短时效 + Refresh,RSA-256,密钥仅存环境或 Secret Store;
- 可观测:结构化日志、Actuator/Micrometer、Prometheus/Grafana、告警;
- 无监控、无备份、无恢复演练的环境不得进入 Production。

## 4. 模块边界与契约
identity(用户/团队/角色/鉴权)与 product(业务实体与流程)边界清晰;OpenAPI 权威契约与运行时一致(契约差异测试);领域模型、状态机、结果 Schema 由对应 Task 的机器契约定义。
