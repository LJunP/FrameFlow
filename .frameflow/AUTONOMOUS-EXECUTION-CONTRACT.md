# FrameFlow Select 自治执行契约

> 状态：用户一次性预授权的本地执行协议  
> 适用目标：从旧 FrameFlow 仓库迁移并完成 `LOCAL_MVP_COMPLETE`  
> 不授权：远端推送、云部署、生产发布、付费资源、真实客户数据和重大产品改向

## 1. Owner 授权的替代方式

用户已通过总提示词固定以下产品决策：

- 产品：AI 生成短视频批量质检与 Top-K 优选；
- 首个模板：`ECOMMERCE_SHORT_AD_V1`；
- 核心对象：Project、Quality Profile、Batch、Candidate、Analysis Run、Finding、Ranking Snapshot、Selection Set；
- 架构：Java 模块化单体 + Python Worker + Next.js Web + PostgreSQL + MinIO + RabbitMQ；
- 非目标：生成、编辑、发布、爆款预测、通用审核、Agent Infra、微服务/Kafka/K8s/Istio；
- 本地完成可由 Harness 代理接受；真实市场、生产和法律结论不能代理批准。

因此，满足自动 Gate 时，Harness 可以记录：

```text
OWNER_PROXY_ACCEPTED_LOCAL
```

不能记录：

```text
MARKET_VALIDATED
PRODUCTION_READY
LEGAL_COMPLIANT
FULLY_AUTONOMOUS_REVIEW
```


## 1.1 BOOK-05 与机器 Master Plan 的关系

BOOK-05 保留产品生命周期、质量 Gate、停止条件和真实试点逻辑；`MASTER-PLAN.yaml` 是本自治包为 `LOCAL_MVP_COMPLETE` 制定的可执行细分，不是第二套产品路线。

映射如下：

```text
BOOK-05 S0 受控转向                 → Master S0
BOOK-05 S1 AI 可行性                → Master S1
BOOK-05 S2 单候选垂直切片           → Master S2 + S3 + S7 的单候选部分
BOOK-05 S3 批量可靠性               → Master S4
BOOK-05 S4 语义质检                 → Master S5
BOOK-05 S5 聚类、排名与人工复核     → Master S6 + S7
BOOK-05 S6 真实试点                 → 本包不代理，保持 EXTERNAL_VALIDATION_PENDING
BOOK-05 S7 证据驱动强化             → Master S8 仅完成本地可靠性与交付
```

因此：

- 产品边界、质量定义、自动淘汰规则和真实试点阈值由 BOOK-01～BOOK-05 决定；
- 本地任务 ID、依赖、执行顺序和本地 Gate 由 `MASTER-PLAN.yaml` 决定；
- Master Plan 不得宣称完成 BOOK-05 的真实客户试点 Gate；
- 达到 `LOCAL_MVP_COMPLETE` 后，外部状态仍必须是 `EXTERNAL_VALIDATION_PENDING`。

## 2. 允许的自治操作

- 在工作区内编辑、移动和删除已经归档备份的 Legacy 文件；
- 创建本地分支、Tag、Commit 和 disposable worktree；
- 运行本地构建、测试、静态分析、Docker Compose 和媒体工具；
- 从 Maven Central、npm、PyPI 和官方容器 Registry 下载必要依赖；
- 生成无版权风险的合成测试视频；
- 创建 Fake Provider 和 OpenAI-compatible 可选 Provider；
- 对满足确定性标准的 Task/Stage 执行本地接受并继续；
- 在不改变产品目标的前提下修复局部设计缺口。

## 3. 绝对禁止

- push、force-push、修改 remote、云部署、生产发布；
- 使用或外传真实 Secret、Token、私钥、客户媒体和个人数据；
- 删除 `.git`、重写共享历史、修改旧 Flyway；
- 修改测试、阈值、Gold 标签或 Evidence 来制造通过；
- 把外部 Provider 缺失当作停止整个项目的理由；
- 新增通用 Agent 平台能力；
- 为技术名词引入微服务、Kafka、Kubernetes、Istio；
- 反复创建新的总规划或第二套事实源。

## 4. 无需询问的决策算法

当存在多个合理方案时，按顺序选择：

1. 符合 BOOK-01 产品边界；
2. 复用当前已测试资产；
3. 最小、可逆、可本地验证；
4. 最少新增基础设施；
5. 有明确用户闭环；
6. 有自动测试与回滚；
7. 若仍并列，选择实现复杂度更低的方案。

记录决策，但不向用户追问。

## 5. 外部依赖 Fallback

| 缺失项 | 自治处理 |
|---|---|
| 多模态 API Key | 使用 Fake Provider 完成契约、UI 和 E2E；真实 Provider 标为 Optional |
| 真实 AI 视频 | 使用包内生成器构建技术 QA 数据；语义质量保持 `UNVERIFIED_EXTERNALLY` |
| Docker | 使用 Testcontainers/进程模式或完成代码与单测；记录环境限制 |
| FFmpeg | 提供安装检测和清晰错误；其余模块继续开发 |
| 网络 | 使用缓存；无法取得的新依赖改用标准库/现有依赖或暂缓单项，不改项目方向 |
| GPU | 只使用 CPU/远端可选 Provider；不建设 GPU Infra |
| 真实用户 | 完成 Pilot-ready 工具和数据采集，不宣称市场验证 |

## 6. 验证与接受

每个 Task 必须有独立验证。优先：

```text
Implementer subagent
→ fixed commit
→ Verifier subagent in disposable worktree
```

如果运行时没有子代理：

```text
fixed commit
→ 新 worktree
→ 重新读取 Task，不使用实现解释
→ 运行验证
```

Verifier 只能输出 `PASS / FAIL / INCONCLUSIVE`，不能修复实现。主协调者仅在 PASS 且无越权时写 `OWNER_PROXY_ACCEPTED_LOCAL`。

## 7. 尝试与停止

同一 Task：

```text
attempt 1
→ root-cause fix attempt 2
→ fallback / narrow
```

禁止第三次盲目重复。只有以下情况允许结束前停止：

- 工作区外不可逆操作是唯一解决方案；
- 必须使用未提供的真实秘密且没有 Fake/Local 替代；
- 发现疑似真实数据泄漏；
- 仓库损坏且无法从 Git 历史恢复；
- 本地硬件无法容纳任何最小实现。

其他情况应记录限制并继续交付可运行本地 MVP。
