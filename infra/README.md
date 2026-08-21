# infra — 四套逻辑隔离环境(目录与模板)

此目录只提供:环境要求、模板与验证脚本。**第一版真实 Dockerfile、Compose、
Nginx 与部署脚本必须由项目所有者在其对应的 HUMAN_CORE(LR3/LR4/LR13/LR14/LR15)
任务中亲手完成**,不得由 Agent 代写完整生产配置。

| 目录 | 环境 | 用途 |
| --- | --- | --- |
| local/ | 个人电脑 | 编码、单元测试、本地 Compose、集成测试 |
| dev/ | 非生产 VPS | 联调、持续部署、功能验证 |
| staging/ | 非生产 VPS | Release Candidate、迁移验证、E2E、回滚验证 |
| production/ | 独立生产 VPS | 真实用户、真实数据、域名、HTTPS、监控、备份 |

隔离要求(见 BOOK-05):数据库、Redis、RabbitMQ VHost、MinIO Bucket、Docker
Network/Volume、环境变量、Secret、域名、日志、备份全部按环境隔离;禁止跨环境
共用生产数据与 Secret。
