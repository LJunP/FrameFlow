# CI 与质量门禁

## 1. Pull Request 必须通过

```text
# 仅 P0-Prep / Git 初始化前执行一次并冻结原始证据
python3 scripts/validate_p0_prep.py
mvn -B clean verify
架构依赖测试
数据库迁移校验
单元测试与 API 测试
静态检查与格式检查
依赖漏洞扫描
```

第一条是 Git 初始化前的 P0-Prep 控制面门禁：校验 Task Capsule Schema、Requirement/Test 注册表、任务依赖、编号、索引和 Evidence 映射，并生成冻结的 pre-Git 原始证据；检测到 `.git` 后会拒绝执行，后续 CI 不得重写 PP 证据，而应使用 P0 及当前阶段的 Receipt/Evidence。没有生成物和真实输出时不得以 Markdown 勾选替代通过结论。

Testcontainers 从每个真实基础设施首次引入时开始使用（P0/M01 起 PostgreSQL；M04-B 起 MinIO；M05 起 Redis；M06 起 RabbitMQ；M11 起 Kafka）；M12-B 负责测试体系强化。进入 M13 后增加服务契约测试；进入 M16 后增加容器构建和 Kubernetes smoke test。

## 2. 分支与提交

- `main` 只接受通过门禁的合并；
- 提交使用 `feat:`、`fix:`、`test:`、`docs:`、`refactor:`、`ops:`；
- 服务边界、数据库所有权、契约和部署策略变更必须关联 ADR；
- 不把密钥、生成物、JFR、GC 原始日志和大文件直接提交到源码，证据使用受控目录或外部归档引用。

## 3. 质量原则

- 覆盖率不是唯一目标，关键业务规则和故障场景优先；
- Mock 不替代关键基础设施集成测试；
- 静态检查失败不得用全局禁用规则绕过；
- 依赖升级需要验证 Spring Boot、Spring Cloud 和 Java 版本兼容；
- 镜像构建必须不包含 Secret，容器以非 root 运行。

## 4. 证据

CI 输出应关联 Git commit、JDK、Maven、容器版本和测试 profile。失败修复和已知限制写入 `docs/09-delivery/evidence-index.md`。
