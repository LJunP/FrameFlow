# 编码与 Git 约定

## 编码

- 先设计、后实现；Controller 不直接写 SQL。
- 领域规则放在 Domain/Application Service，不把所有规则堆到 Controller。
- 每项关键规则有测试；状态、权限、事务、幂等、消息和补偿必须有明确边界。
- 模块之间不能访问彼此 Repository、Mapper、Entity 或表；ArchUnit 负责验证。
- 数据库结构变化使用 Flyway 迁移；服务阶段每个服务维护自己的迁移目录。
- API/事件契约变化必须有兼容性说明和回归测试。
- Dockerfile、Helm、Kubernetes、Istio 配置必须有验证和回滚说明。

## 提交与分支

- 提交格式：`feat:`、`fix:`、`test:`、`docs:`、`refactor:`、`ops:`。
- `main` 只接收通过构建、测试、静态检查和必要集成测试的变更。
- 每个模块或服务使用独立 feature 分支；实现、审查和验证角色分离。
- 架构取舍写入 `docs/02-architecture/adr/`；服务边界、数据所有权和部署职责变化必须关联 ADR。
- 不允许一个提交混入多个服务的无关变更。

## 安全与生成物

- 不提交 `.env`、生成物、日志、真实素材、JFR/GC 大文件或任何密钥。
- 证据文件只保存脱敏输出，并关联 Git commit、环境和原始报告位置。
- 不读取或复用 `jcm_media_api` 的配置、凭据、地址或测试环境。
