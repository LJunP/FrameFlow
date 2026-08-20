# BOOK-05：FrameFlow Select 开发路线图与阶段门禁

> **Autopilot Profile 1.0.0**：用户已通过 `AUTONOMOUS-EXECUTION-CONTRACT.md` 预先授权本地、可回退的任务与阶段接受。真实客户试点、市场验证、生产发布和法律结论仍不得由 Agent 代替。缺少真实媒体或 Provider 时允许先达到 `LOCAL_MVP_COMPLETE`，但必须保持 `EXTERNAL_VALIDATION_PENDING`。


> 版本：1.0.0  
> 日期：2026-08-20  
> 权威范围：开发顺序、阶段准入、验收证据、停止条件与发布口径  
> 上位文档：[BOOK-01 项目宪章](./BOOK-01-项目宪章与权威地图.md)  
> 配套文档：[BOOK-02 产品蓝图](./BOOK-02-产品与用户体验蓝图.md)、[BOOK-03 质量与评测规范](./BOOK-03-AI视频质检标准与评测规范.md)、[BOOK-04 架构设计](./BOOK-04-领域模型与系统架构设计.md)

---

## 1. 本路线图解决什么问题

本路线图不是按技术名词排列的学习清单，也不是承诺必须走到微服务、Kafka、Kubernetes 或 Istio 的长期幻想路线。

它只回答五个问题：

1. 当前仓库从哪里出发；
2. 下一阶段为什么值得做；
3. 完成后必须拿出什么可验证结果；
4. 未达到什么条件就不能继续；
5. 何时应当停止、回退或调整产品切口。

路线图采用**证据驱动的阶段门禁**：

```text
问题假设
  → 最小实现或实验
  → 可重复验证
  → 独立复核
  → 产品所有者批准
  → 进入下一阶段
```

禁止采用：

```text
文档写完
  → 任务状态改为 DONE
  → 默认假设产品成立
  → 继续扩大范围
```

---

## 2. 当前真实基线

### 2.1 已经存在且可复用

当前 `FrameFlow(2).zip` 所含仓库已经具备：

- Java 多模块工程；
- Spring Boot、Spring Security；
- PostgreSQL 16、Flyway；
- 用户、团队、成员、JWT、Refresh Token；
- 团队级 RBAC；
- PostgreSQL 幂等记录；
- OpenAPI 契约与运行时差异验证；
- Testcontainers、ArchUnit、JUnit；
- GitHub Actions；
- P0、M01、M01F、M01H 的任务与 Evidence 基线。

这些资产构成新项目的**工程起点**，不应重写。

### 2.2 当前不存在

当前仓库尚没有：

- FrameFlow Select 新领域代码；
- 项目、质量配置、批次、候选视频、分析结果、排名与选择集；
- Web 前端；
- MinIO / S3 对象存储；
- RabbitMQ；
- Python AI Worker；
- FFmpeg / OpenCV 分析；
- ASR、OCR、多模态模型；
- 视频相似度、聚类、Top-K；
- AI 质量评测数据集；
- 试点用户证据。

### 2.3 当前状态的正式表述

在完成 Stage 0 之前，项目状态只能写成：

```text
Legacy engineering baseline: M01-H accepted with pending workspace cleanup
FrameFlow Select product pivot: APPROVED, NOT YET APPLIED
Next dispatchable work: Stage 0 pivot tasks only
Old M02+ product tasks: NOT DISPATCHABLE
```

不得写成：

```text
FrameFlow Select 已开发
AI 视频质检能力已完成
产品 MVP 已可用
```

---

## 3. 路线图组织方式

项目使用四条同步轨道，但每个阶段只交付一个统一结果。

| 轨道 | 负责内容 | 不能单独宣布阶段完成 |
|---|---|---|
| Product | 用户流程、页面、权限、业务闭环 | 只有原型，没有真实分析结果 |
| AI Quality | 检测器、质量规范、数据集、评测 | 只有 Notebook，没有产品闭环 |
| Engineering | Java、Python、Web、存储、队列、测试、运维 | 只有基础设施，没有用户价值 |
| Validation | 人工标注、试点、指标、失败分析 | 只有访谈，没有可重复系统 |

阶段完成必须同时满足该阶段规定的产品、AI、工程和验证条件。

---

## 4. 总体阶段图

```text
S0 受控转向与基线冻结
  ↓
S1 AI 质检可行性实验
  ↓
S2 单候选端到端垂直切片
  ↓
S3 批量异步处理与可靠性
  ↓
S4 Brief/Prompt 语义质检与证据
  ↓
S5 聚类、排名、Top-K 与人工复核
  ↓
S6 电商短视频试点 MVP
  ↓
S7 证据驱动的强化与扩展
```

任何阶段都不得越过其前一阶段 Gate。

允许并行的只有同一阶段中**相互独立且具有明确集成点**的任务，不允许同时推进两个产品阶段。

---

# 5. Stage 0：受控转向与基线冻结

## 5.1 阶段目标

把当前“视频协作平台”仓库转成一个事实一致、可回退、可开始实验的 FrameFlow Select 基线。

本阶段不新增用户业务功能。

## 5.2 必须完成

### 仓库与历史

1. 处理当前工作树中未提交或异常的 Evidence 文件；
2. 验证当前 M01-Hardening 的真实测试结果；
3. 为旧产品基线创建只读 Tag：

```text
frameflow-collaboration-v0.2-m01h
```

4. 创建转向分支：

```text
pivot/frameflow-select
```

5. 禁止重写已有 Flyway migration；
6. 禁止为了“看起来干净”删除旧 Git 历史。

### 产品与文档

1. 将本规划包纳入仓库；
2. 重写根 `README.md` 和 `project-status.md`；
3. 旧 PRD、M02～M17 计划、微服务/Kubernetes/Istio 路线进入：

```text
docs/archive/frameflow-collaboration/
```

4. 旧任务标记为：

```text
CANCELLED_BY_PIVOT
```

不得标记为 `DONE`；
5. 通用 Agent Control Plane、Task Capsule、Capability Grant、Agent Receipt 等产品设计移出 FrameFlow 权威文档；SourceLens-AIOS 可继续使用其副本。

### 代码与安全

1. 正式交付包排除：

```text
.git/
target/
__MACOSX/
.DS_Store
.zcode/
data/jwt/*.pem
```

2. 新建角色迁移方案：

```text
OWNER
OPERATOR
REVIEWER
VIEWER
```

3. 通过新的 Flyway migration 迁移旧角色，不修改历史 migration；
4. 设计密钥生成和本地开发流程，禁止分发本地私钥。

### 技术兼容性 Spike

在独立分支验证：

```text
JDK 21
Spring Boot 4.1.x
MyBatis-Plus Boot 4 Starter
springdoc 3.x
现有身份与契约测试
```

Spike 必须输出二选一决策：

```text
A. 通过：采用 JDK 21 + Spring Boot 4.1.x
B. 未通过：采用 JDK 21 + 经 ADR 批准的 Boot 3.x 版本
```

不允许在产品阶段同时维护两个 Spring Boot 主版本。

## 5.3 明确不做

- 不实现项目、批次或候选视频 API；
- 不接入 MinIO、RabbitMQ 或 AI Provider；
- 不创建新的微服务；
- 不开始前端；
- 不扩写新的 48 周技术路线；
- 不为了兼容旧任务而保留旧产品语义。

## 5.4 准入条件

本阶段可立即开始，无前置产品 Gate。

## 5.5 退出 Gate：`PIVOT_BASELINE_ACCEPTED`

必须全部满足：

- [ ] 工作树干净；
- [ ] 旧基线 Tag 存在且可以 checkout；
- [ ] 新转向分支存在；
- [ ] M01 自动化测试通过或明确记录无法复现的原因；
- [ ] 六本权威手册进入仓库；
- [ ] 旧 M02+ 任务全部失去派发资格；
- [ ] 旧产品文档已归档且带 `LEGACY / NON-AUTHORITATIVE` 标记；
- [ ] 角色迁移方案和 Flyway 迁移编号已确定；
- [ ] 正式打包不含私钥和构建垃圾；
- [ ] Java 21 / Boot 版本 ADR 已批准；
- [ ] 独立 Reviewer 确认新旧事实源没有并存冲突。

## 5.6 必要 Evidence

```text
evidence/pivot/
├── baseline-commit.txt
├── legacy-tag.txt
├── worktree-status.txt
├── current-test-report/
├── compatibility-spike.md
├── package-manifest.json
├── archived-docs-manifest.json
├── cancelled-tasks.json
└── reviewer-verdict.md
```

## 5.7 失败处理

若 Stage 0 无法使事实源收敛：

1. 暂停所有功能开发；
2. 回到旧 Tag；
3. 列出仍冲突的事实；
4. 由产品所有者逐项裁决；
5. 禁止 Agent 自行选择一份“看起来最新”的文档继续开发。

---

# 6. Stage 1：AI 质检可行性实验

## 6.1 阶段目标

在建设完整产品之前证明：

> 对一批 AI 生成短视频，系统能够以可接受的准确率和成本发现至少三类有价值问题，并提供足够证据支持人工筛选。

这是产品生死 Gate，不是技术演示。

## 6.2 实验范围

### 数据

至少构建：

```text
50 条 AI 生成短视频
10 个 Prompt / Brief 组
每组至少 3 个候选
覆盖至少 2 个生成模型或来源
时长以 5～60 秒为主
```

### 首批问题类别

必须选择三类以上、且至少包括：

1. 一类确定性技术问题：
   - 黑帧；
   - 冻结；
   - 分辨率/宽高比错误；
   - 静音或音频异常；
2. 一类批次问题：
   - 重复或近重复；
3. 一类语义或生成问题：
   - Prompt 关键要求缺失；
   - 产品/主体不一致；
   - 明显结构性生成瑕疵。

### 实现形式

优先使用：

```text
experiments/video-qa-feasibility/
```

可以包含 Python CLI、Notebook 和离线报告，但必须：

- 锁定依赖；
- 固定数据集版本；
- 输出机器可读结果；
- 能由另一台环境重复执行；
- 不把实验代码误标成生产 Worker。

## 6.3 必须回答的问题

1. 哪些问题可由规则或 FFmpeg 稳定检测？
2. 哪些问题需要专用模型？
3. 哪些问题需要多模态模型？
4. 多模态模型能否给出可靠时间码或关键帧证据？
5. 同批次视频能否被合理聚类和排序？
6. 每分钟视频的分析成本是多少？
7. 哪些问题误报严重，不应进入 MVP？
8. 哪些检测器在不同视频生成模型之间失效？

## 6.4 明确不做

- 不建设完整 Java 业务模块；
- 不建设完整 Web UI；
- 不做多租户 SaaS；
- 不做自动发布；
- 不做通用内容合规平台；
- 不做开放式多 Agent 编排；
- 不因单个漂亮 Demo 直接进入 MVP。

## 6.5 准入条件

必须已通过 `PIVOT_BASELINE_ACCEPTED`。

## 6.6 退出 Gate：`AI_FEASIBILITY_ACCEPTED`

采用 BOOK-03 的 Feasibility Gate，至少满足：

- [ ] 数据集规模和来源符合要求；
- [ ] 三类以上高价值问题可检测；
- [ ] 选中问题类别 Precision 不低于 0.75；
- [ ] 选中严重问题 Recall 不低于 0.70；
- [ ] Brief 语义断言与人工一致率不低于 0.70；
- [ ] 至少 95% 的 Finding 带可查看证据；
- [ ] 重复检测有明确阈值和误差分析；
- [ ] 记录单视频和单分钟成本；
- [ ] 至少一位独立标注者复核；
- [ ] 失败样本被纳入 Hard Case Set；
- [ ] 输出“保留、推迟、放弃”的检测器清单。

## 6.7 必要 Evidence

```text
evidence/feasibility/
├── dataset-manifest.json
├── annotation-guide.md
├── labels/
├── experiment-config.json
├── raw-results/
├── metrics.json
├── cost-report.json
├── failure-analysis.md
├── detector-decision-record.md
└── reviewer-verdict.md
```

## 6.8 停止或调整条件

出现以下任一情况，不得直接进入 Stage 2：

- 只有通用技术规格检测有效，AI 生成视频特有价值无法证明；
- 误淘汰率过高且无法通过人工复核模式控制；
- 多模态结果无法给出稳定证据；
- 单位成本明显高于人工初筛价值；
- 数据集来源过于单一，结果无法泛化；
- 用户真正想要的是生成、剪辑或投放，而不是筛选。

处理方式只能是：

```text
收缩问题类别
或调整首个用户切口
或终止该产品假设
```

不能通过放宽指标或隐藏失败样本强行通过。

---

# 7. Stage 2：单候选端到端垂直切片

## 7.1 阶段目标

让一个真实用户能够从浏览器完成：

```text
登录
→ 创建项目
→ 创建质量配置
→ 创建批次
→ 上传 1 条视频
→ 启动分析
→ 查看带时间码的 Finding
→ 人工确认或驳回
```

这是第一个真正的产品切片。

## 7.2 必须实现

### Java

- 新角色模型；
- Project；
- Quality Profile 与不可变版本；
- Batch；
- Candidate 与不可变媒体版本；
- Analysis Run；
- Finding；
- Human Review；
- MinIO 上传会话与对象元数据；
- RabbitMQ 最小 Command / Result 契约；
- 事务、幂等、租户隔离；
- OpenAPI 契约。

### Python

- Worker 基座；
- ffprobe 元数据提取；
- 黑帧、冻结、静音/音频存在性等首批确定性检测；
- 关键帧和缩略图生成；
- 标准 Analysis Result 输出；
- Provider Adapter 接口，但不要求启用昂贵多模态 Provider。

### Web

- 登录；
- Project 列表与创建；
- Quality Profile 最小编辑器；
- Batch 创建；
- 预签名上传；
- 处理进度；
- 视频播放器；
- 时间码 Finding；
- 接受/驳回。

## 7.3 架构纪律

- 一个 Java 应用；
- 一个 Python Worker；
- 一个 Web 应用；
- PostgreSQL；
- MinIO；
- RabbitMQ；
- 不拆微服务；
- 不引入 Redis、Kafka、Elasticsearch；
- 所有异步状态以 PostgreSQL 为最终事实源；
- RabbitMQ 消息不得成为唯一业务事实。

## 7.4 准入条件

- `AI_FEASIBILITY_ACCEPTED`；
- 首批生产检测器清单已批准；
- OpenAPI、消息 Schema、数据模型达到 `CONTRACT_READY`；
- 正式上传安全边界已定义；
- 当前角色迁移已完成。

## 7.5 退出 Gate：`SINGLE_CANDIDATE_SLICE_ACCEPTED`

- [ ] 一个新用户可在空数据库上完成完整流程；
- [ ] 不需要手动写数据库；
- [ ] 上传、创建 Analysis Run 和结果回写具备幂等性；
- [ ] Worker 重复投递不会重复创建 Finding；
- [ ] Java 或 Worker 重启后任务状态可恢复；
- [ ] 每个 Finding 可跳转到对应时间码或关键帧；
- [ ] 跨团队访问被拒绝；
- [ ] OpenAPI 与运行时一致；
- [ ] Java、Python、Web 测试均通过；
- [ ] 对一个损坏视频有明确失败状态，而非永久处理中；
- [ ] 用户可确认或驳回 Finding；
- [ ] 本地一条命令可启动完整环境；
- [ ] 端到端演示可以在干净环境重复。

## 7.6 必要 Evidence

```text
evidence/single-slice/
├── environment-manifest.json
├── openapi-validation.txt
├── message-schema-validation.txt
├── migration-validation.txt
├── test-reports/
├── e2e-run.json
├── screenshots/
├── sample-video-manifest.json
├── security-tests.md
├── restart-recovery.md
└── reviewer-verdict.md
```

## 7.7 失败处理

若端到端切片被基础设施复杂性拖住：

1. 不删除产品能力来保护架构；
2. 优先删除非必要基础设施；
3. 保留用户闭环；
4. 记录真正瓶颈；
5. 只有证据表明 RabbitMQ 是阻塞点时，才允许临时降级为数据库轮询适配器，并通过 ADR 记录恢复条件。

---

# 8. Stage 3：批量异步处理与可靠性

## 8.1 阶段目标

把单候选切片扩展成真正的批量质检系统：

```text
一次导入 20～100 条候选
→ 并行处理
→ 部分失败不阻塞全批次
→ 可取消、可重试、可观察
→ 输出统一批次结果
```

## 8.2 必须实现

### 批量导入

- 多文件并发上传；
- 文件指纹与重复上传检测；
- 上传失败重试；
- 候选清单预检；
- Batch 封存后才可分析。

### 调度

- 每个 Candidate 拆分 Stage Job；
- Worker concurrency 可配置；
- ACK / NACK；
- 指数退避；
- 最大重试次数；
- DLQ；
- 超时；
- 取消；
- Batch 级进度聚合；
- 部分成功状态。

### 可靠性

- Outbox 或等价的可靠发布机制；
- Command 和 Result 幂等；
- Provider request ID；
- 重放不产生重复账单或重复结果；
- Run 和 Stage Job 的状态转换校验；
- 毒消息隔离；
- 临时文件清理；
- 对象生命周期策略。

### 可观察性

- Batch、Run、Candidate、Stage Job 统一 Trace 关联；
- 队列深度；
- 执行耗时；
- 重试次数；
- 失败类别；
- 单候选成本；
- Worker 使用率。

## 8.3 明确不做

- 不为“高并发想象”拆微服务；
- 不引入 Kafka；
- 不引入 Kubernetes；
- 不做跨地域；
- 不做自动弹性伸缩；
- 不做复杂工作流编辑器。

## 8.4 准入条件

必须通过 `SINGLE_CANDIDATE_SLICE_ACCEPTED`，且单候选流程在真实环境稳定。

## 8.5 退出 Gate：`BATCH_ENGINE_ACCEPTED`

- [ ] 能处理至少一个 100 候选测试批次；
- [ ] 单候选失败不会阻塞其他候选；
- [ ] 可从 UI 看到候选与阶段级进度；
- [ ] Worker 被终止后，未完成任务可恢复或进入明确失败状态；
- [ ] 重复消息不会重复落库；
- [ ] 取消操作有明确边界和终态；
- [ ] DLQ 可查看、可人工决定重放或放弃；
- [ ] 至少 95% 的有效输入产生可用分析结果；
- [ ] 资源和成本指标可查询；
- [ ] 所有状态迁移都有测试；
- [ ] 100 候选负载测试结果被保存；
- [ ] 没有无限重试或永久 `PROCESSING`。

## 8.6 必要 Evidence

```text
evidence/batch-engine/
├── load-test-config.json
├── load-test-results.json
├── queue-metrics.json
├── retry-and-dlq-tests.md
├── crash-recovery-tests.md
├── idempotency-tests.md
├── state-transition-tests.md
├── cost-and-resource-baseline.json
└── reviewer-verdict.md
```

---

# 9. Stage 4：Brief / Prompt 语义质检与证据

## 9.1 阶段目标

让系统不只检测文件和画面技术问题，还能回答：

> 这条视频是否满足本批次的 Prompt、Brief 和电商广告要求？

## 9.2 必须实现

### Quality Profile

支持：

- HARD_CONSTRAINT；
- SEMANTIC_ASSERTION；
- PROHIBITED_ASSERTION；
- RANKING_PREFERENCE；
- 规则版本；
- Profile 发布与冻结；
- 基于模板创建。

### 媒体理解

按评测结论选择：

- ASR；
- OCR；
- 镜头切分；
- 关键帧选择；
- Logo / 产品参考图匹配；
- 多模态模型；
- 文本与视觉证据关联。

### 语义结果

每个语义 Finding 至少包含：

```text
ruleId
assertionId
verdict
severity
confidence
startMs / endMs
keyframeRefs
sourceRequirement
explanation
provider
model
promptVersion
```

### 有界 AI

- 一次 Stage Job 使用固定 DAG；
- 结构化输入和输出；
- 无开放式递归 Agent；
- 最大模型调用数；
- 最大 Token / 金额预算；
- 超限进入 `NEEDS_REVIEW`；
- 模型输出不得直接写业务表；
- Java 校验 Schema 后落库。

## 9.3 自动决策边界

MVP 内：

- 确定性硬规则可以自动 `REJECT`；
- 高精度专用检测器可在规则批准后自动 `REJECT`；
- 多模态语义判断默认只能：

```text
PASS_HINT
REVIEW
RANKING_PENALTY
```

不得单独自动淘汰，除非通过独立评测和规则审批。

## 9.4 准入条件

- `BATCH_ENGINE_ACCEPTED`；
- 对应 Provider 的数据处理边界已批准；
- Gold Test Set 已建立；
- Prompt、模型和 Schema 可版本化；
- 预算模式已定义。

## 9.5 退出 Gate：`SEMANTIC_QA_ACCEPTED`

- [ ] 至少支持一个电商广告 Quality Profile 模板；
- [ ] 至少支持 5 条可测试语义断言；
- [ ] 语义断言与人工一致率达到 BOOK-03 要求；
- [ ] 每个语义 Finding 有来源条款和证据；
- [ ] 模型无法判断时返回 `UNKNOWN`，不得伪造确定答案；
- [ ] Provider 失败不会破坏整个批次；
- [ ] Provider、模型、Prompt、参数和成本全部可追踪；
- [ ] 数据集未泄漏到 Prompt 调优过程；
- [ ] 低置信度结果进入人工复核；
- [ ] 关键语义规则具有回归测试；
- [ ] 模型版本变更会触发评测 Gate；
- [ ] 成本预算超限可以停止后续昂贵分析。

## 9.6 必要 Evidence

```text
evidence/semantic-qa/
├── quality-profile-version.json
├── prompt-registry.json
├── provider-manifest.json
├── gold-set-manifest.json
├── evaluation-results.json
├── calibration-report.md
├── schema-validation.txt
├── cost-breakdown.json
├── failure-cases/
└── reviewer-verdict.md
```

---

# 10. Stage 5：聚类、排名、Top-K 与人工复核

## 10.1 阶段目标

把“逐条找问题”提升成用户真正需要的决策结果：

```text
从一批候选中减少重复、保留多样性，并选出最值得人工终审的 Top-K。
```

## 10.2 必须实现

### 相似度与聚类

- 视频级视觉 Embedding；
- 音频或转录相似度；
- 可配置的近重复阈值；
- Cluster 代表候选；
- 用户拆分或合并聚类；
- 聚类算法和模型版本记录。

### 排名

- 使用多维质量向量；
- 明确硬性 Gate 与排序的顺序；
- 权重来自 Quality Profile；
- 可解释的加分与扣分；
- Diversity Penalty；
- Ranking Snapshot 不可变；
- 用户调整权重后产生新 Snapshot，不覆盖旧结果。

### 人工复核

- Candidate Matrix；
- 两两比较；
- Finding 确认、驳回、改级别；
- Candidate 接受、复核、淘汰；
- Top-K 手工锁定；
- Review 与 AI 原结果分开保存；
- 所有覆盖操作有理由和审计。

### Evaluation Dashboard

- GCR；
- False Reject Rate；
- Top-K Recall；
- Finding Confirmation Rate；
- Full-watch Reduction Rate；
- 成本；
- 延迟；
- 模型和 Prompt 对比。

## 10.3 准入条件

- `SEMANTIC_QA_ACCEPTED`；
- 批次中至少有可比较的多个候选；
- Pairwise 标注数据存在；
- 排名公式和权重版本已批准。

## 10.4 退出 Gate：`MVP_EVALUATION_ACCEPTED`

至少满足 BOOK-03 的 MVP Gate：

- [ ] Gold Test Set 至少 200 条视频、30 个 Prompt/Brief 组；
- [ ] GCR 不低于 0.90；
- [ ] 自动误淘汰率不高于 0.05；
- [ ] Top-K Recall 不低于 0.85；
- [ ] 严重技术问题 Recall 不低于 0.90；
- [ ] 可用 Analysis Run 比例不低于 0.95；
- [ ] 相同输入与相同版本可复现 Ranking Snapshot；
- [ ] 人工可以解释每个 Top-K 推荐的主要原因；
- [ ] 聚类误差有人工修正路径；
- [ ] 评测 Dashboard 基于锁定数据，不使用演示数据伪装；
- [ ] 阈值调整没有读取 Gold Test Set 的答案进行过拟合；
- [ ] 完整用户闭环 E2E 通过。

## 10.5 必要 Evidence

```text
evidence/mvp-evaluation/
├── dataset-version.json
├── ranking-config.json
├── cluster-evaluation.json
├── gate-metrics.json
├── pairwise-evaluation.json
├── user-override-analysis.json
├── cost-and-latency.json
├── e2e-report.md
├── demo-batch-export/
└── reviewer-verdict.md
```

---

# 11. Stage 6：电商短视频试点 MVP

## 11.1 阶段目标

在真实或高度接近真实的电商短视频批次中证明：

> FrameFlow Select 能显著减少人工完整观看量，同时不会大量漏掉优质候选。

## 11.2 首个试点模板

```text
电商商品短视频广告
时长：5～60 秒
常见比例：9:16
每批候选：20～300
```

至少支持：

- 时长、比例、分辨率；
- 前 N 秒出现商品；
- 商品或 Logo 可见；
- 指定卖点出现；
- CTA 出现；
- 禁止竞品 Logo 或禁止词；
- 字幕存在与基础可读性；
- 黑帧、冻结、静音等技术问题；
- 近重复聚类；
- Top-K；
- 人工终审；
- CSV / JSON / HTML 报告。

## 11.3 试点方式

### Phase A：Shadow Mode

系统分析真实批次，但不影响人工原流程。

对比：

```text
AI 结果
vs
人工完整审核结果
```

### Phase B：Assisted Mode

系统先筛选，人工仍可查看全部候选，并记录：

- 被系统隐藏但人工找回的优质候选；
- 系统推荐但人工淘汰的候选；
- 人工节省的完整观看次数；
- 用户推翻 Finding 的原因。

### Phase C：Limited Gate

只有已通过规则审批的硬规则可以自动淘汰；其余结果仍进入人工复核。

## 11.4 准入条件

- `MVP_EVALUATION_ACCEPTED`；
- 隐私、数据保留、删除和 Provider 边界已向试点用户说明；
- 可以导出完整结果；
- 有故障降级方案；
- 有人工兜底；
- 用户数据与评测数据明确分离。

## 11.5 退出 Gate：`PILOT_VALUE_PROVEN`

至少完成三批真实或准真实试点，并满足：

- [ ] Full-watch Reduction Rate 不低于 50%；
- [ ] Good Candidate Loss Rate 不高于 5%；
- [ ] Finding Confirmation Rate 不低于 60%；
- [ ] 单批次结果可被用户理解和复核；
- [ ] 单位分析成本不高于估算人工价值的 25%；
- [ ] 至少一位目标用户愿意继续使用或提供下一批数据；
- [ ] 试点中无严重租户隔离或数据泄漏；
- [ ] 没有依赖人工修改数据库恢复任务；
- [ ] 失败和误判已经进入风险登记册及 Hard Case Set；
- [ ] 用户确认产品核心价值是“筛选与优选”，而不是生成或剪辑。

## 11.6 必要 Evidence

```text
evidence/pilot/
├── pilot-agreement-summary.md
├── privacy-disclosure.md
├── batch-manifests/
├── shadow-mode-comparison.json
├── assisted-mode-metrics.json
├── user-feedback.md
├── false-reject-cases/
├── cost-value-analysis.json
├── incident-summary.md
└── owner-verdict.md
```

## 11.7 失败处理

如果试点未证明价值，优先按以下顺序处理：

1. 检查首个用户切口是否错误；
2. 检查筛选规则是否过宽；
3. 检查用户真正的决策单位是否不是 Batch；
4. 检查成本是否由昂贵模型调用造成；
5. 检查用户是否只需要 API 而不是完整 Web；
6. 检查用户是否更重视某一专用检测能力。

不得直接通过：

```text
增加更多模型
增加多 Agent
拆更多服务
增加更多文档
```

来掩盖价值未被证明的问题。

---

# 12. Stage 7：证据驱动的强化与扩展

## 12.1 进入条件

只有 `PILOT_VALUE_PROVEN` 后才能进入。

Stage 7 不存在固定技术清单。每个扩展都必须由真实瓶颈触发。

## 12.2 可能方向及触发证据

| 方向 | 允许进入的证据 |
|---|---|
| API 产品 | 至少两个外部系统请求自动提交批次 |
| SaaS 配额/计费 | 有持续试用和资源成本分摊需求 |
| 更多垂直模板 | 当前电商模板已稳定，且有新场景数据 |
| 私有化部署 | 用户数据政策不允许公有云处理 |
| GPU Worker | 真实分析负载和模型证明需要本地推理 |
| Kubernetes | 单机/Compose 已无法满足可用性或调度 |
| 拆服务 | 模块在团队、发布或扩缩容上形成独立压力 |
| Redis | 明确存在跨实例缓存、限流或临时状态需求 |
| Elasticsearch | PostgreSQL 检索已被基准测试证明不足 |
| 多模型路由 | 同一指标上不同模型有稳定成本/质量优势 |
| 自动发布连接器 | 质量 Gate 已足够稳定且用户明确需要 |

## 12.3 默认仍然禁止

没有证据时继续禁止：

- Kafka；
- Istio；
- 服务网格；
- 通用工作流编辑器；
- 通用 Agent 平台；
- 自研基础模型；
- “爆款预测”；
- 无法律依据的版权或合规最终判定。

---

# 13. 阶段与发布口径

| 阶段 Gate | 可使用的外部口径 |
|---|---|
| `PIVOT_BASELINE_ACCEPTED` | 已完成产品转向和工程基线收敛 |
| `AI_FEASIBILITY_ACCEPTED` | 已验证若干视频质检能力的离线可行性 |
| `SINGLE_CANDIDATE_SLICE_ACCEPTED` | 内部 Alpha，可完成单视频端到端分析 |
| `BATCH_ENGINE_ACCEPTED` | 内部批量 Alpha，具备可靠异步处理 |
| `SEMANTIC_QA_ACCEPTED` | 支持受约束的 Brief/Prompt 语义质检 |
| `MVP_EVALUATION_ACCEPTED` | MVP Candidate，通过锁定数据集评测 |
| `PILOT_VALUE_PROVEN` | Pilot Validated，真实批次价值已获得证据 |

禁止提前使用：

```text
生产级
企业级
高可用
自动替代人工
行业领先
准确率 99%
```

除非有直接、可复核的证据支持对应表述。

---

# 14. Task 生命周期与派发门禁

## 14.1 标准状态

```text
PLANNED
  → DRAFT
  → CONTRACT_READY
  → READY_FOR_DISPATCH
  → IN_PROGRESS
  → VERIFYING
  → ACCEPTED
```

异常状态：

```text
BLOCKED
REJECTED
CANCELLED
CANCELLED_BY_PIVOT
SUPERSEDED
```

## 14.2 `READY_FOR_DISPATCH` 条件

每个任务必须具备：

- 对应 Stage；
- 明确目标和非目标；
- Requirement ID；
- 前置 Task ID；
- Read Set；
- Write Set；
- 允许命令；
- 网络策略；
- 数据/密钥边界；
- 可执行验收；
- 测试与 Evidence 映射；
- 预算；
- 回滚方式；
- 审批点；
- 独立验证者。

缺一项时，不得派发给 Agent。

## 14.3 验收映射

采用：

```text
Requirement
  → Task
  → Acceptance Criterion
  → Test Case
  → Evidence Artifact
  → Commit / PR
```

不得使用“若干测试通过”作为全部验收证据。

---

# 15. 测试策略随阶段演进

| 阶段 | 必须测试 |
|---|---|
| S0 | 旧基线回归、迁移兼容性、打包清洁度 |
| S1 | 离线数据集、检测器指标、成本与失败分析 |
| S2 | 单元、数据库集成、对象存储、消息契约、OpenAPI、E2E |
| S3 | 重复投递、重启、超时、取消、DLQ、100 候选负载 |
| S4 | Prompt/模型回归、Schema、证据、预算、Provider 降级 |
| S5 | 聚类、排序、Top-K、Gold Set、防数据泄漏、完整 E2E |
| S6 | Shadow/Assisted 对照、用户指标、故障演练、删除流程 |

AI 评测失败不得通过修改普通单元测试掩盖；普通测试通过也不能替代 AI 评测。

---

# 16. 数据迁移纪律

1. 已执行的 Flyway migration 不得修改；
2. 角色变化、新领域表和约束使用新 migration；
3. 每个 migration 必须在空库和旧基线升级路径上测试；
4. 媒体对象删除与数据库删除分阶段执行；
5. Analysis Result 和 Ranking Snapshot 默认不可变；
6. 需要修正时创建新版本或新 Run；
7. 破坏性迁移必须有数据导出和恢复演练；
8. 不使用“删除数据库重新来过”作为正式回滚方案。

---

# 17. 分支、提交与回滚

## 17.1 分支原则

- `main` 始终保持可验证；
- 每个任务使用独立分支；
- 一个任务只解决一个主要验收目标；
- 禁止在功能分支顺手执行大规模无关重构；
- AI 实验与生产代码分开提交。

## 17.2 回滚

标准回滚优先级：

```text
Feature Flag / Rule Disable
  → Provider Version Rollback
  → Config / Prompt Version Rollback
  → Git Revert
  → Database Forward Fix
  → Object Restore
```

禁止：

- 删除 `.git`；
- 擅自 rewrite 已共享历史；
- 修改旧 migration；
- 删除失败 Evidence；
- 删除误判样本以改善指标。

---

# 18. Evidence 标准目录

每个任务建议保存：

```text
evidence/<stage>/<task-id>/
├── task.json
├── environment.json
├── commands.json
├── test-results/
├── metrics.json
├── artifacts.json
├── known-limitations.md
├── verifier-verdict.md
└── manifest.json
```

`manifest.json` 至少记录：

```text
Task ID
Commit SHA
数据集版本
Quality Profile 版本
模型/Provider 版本
Prompt 版本
Schema 版本
执行时间
工具版本
Artifact 哈希
最终结论
```

Evidence 不能只是一张截图，也不能只保存 Agent 的自然语言总结。

---

# 19. 当前旧任务处置

当前仓库中原视频协作业务的任务处理原则：

| 任务类型 | 处理 |
|---|---|
| P0 / M01 / M01F / M01H | 保留历史状态和 Evidence，不重写结论 |
| M02 项目与 Brief 任务 | `CANCELLED_BY_PIVOT`；新 Project/Profile 任务重新编号 |
| M04F / M08F 等未来任务 | `CANCELLED_BY_PIVOT` 或移入 Legacy 目录 |
| 微服务、Kafka、K8s、Istio 任务 | 取消固定排期；只有 Stage 7 证据触发后重建 |
| 通用 Agent 控制任务 | 移交 SourceLens-AIOS，不进入 FrameFlow 产品 Backlog |

新任务不得复用旧 ID 表示不同语义。

---

# 20. 路线图变更规则

## 20.1 可直接调整

在不改变 Stage 目标和 Gate 的前提下，可调整：

- UI 组件；
- 具体库；
- 内部类名；
- 检测器实现；
- 测试工具；
- 同阶段任务顺序。

## 20.2 必须提交变更申请

以下变化必须按 BOOK-01 的 L2/L3 变更处理：

- 从电商短视频切到其他首要用户；
- 从筛选与优选切到生成或剪辑；
- 将通用 Agent Infra 纳入 FrameFlow；
- 移除人工终审；
- 允许语义模型直接自动淘汰；
- 改变 North Star 或 GCR 护栏；
- 跳过 Feasibility 或 Pilot Gate；
- 提前拆微服务；
- 把 SourceLens 变成运行时强依赖。

---

# 21. 路线图完成定义

本路线图不是以“所有 Stage 都做完”为成功。

真正成功是：

```text
在最小必要阶段证明用户价值，
形成可持续迭代的真实产品，
并在没有证据时拒绝继续扩大技术范围。
```

因此：

- 若 Stage 1 失败，停止产品化是正确结果；
- 若 Stage 6 已证明价值，但暂不需要 Stage 7 技术扩展，也是正确结果；
- 若模块化单体长期足够，就不拆微服务；
- 若某个昂贵 AI 检测器不提高用户决策质量，就永久删除；
- 若用户只需要 API，则可以缩减前端，而不是坚持原先设想。

**路线图服务于产品证据，产品不服务于路线图。**
