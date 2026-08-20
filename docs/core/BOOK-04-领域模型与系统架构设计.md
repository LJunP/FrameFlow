# BOOK-04：FrameFlow Select 领域模型与系统架构设计

> **Autopilot Profile 1.0.0**：用户已通过 `AUTONOMOUS-EXECUTION-CONTRACT.md` 预先授权本地、可回退的任务与阶段接受。真实客户试点、市场验证、生产发布和法律结论仍不得由 Agent 代替。缺少真实媒体或 Provider 时允许先达到 `LOCAL_MVP_COMPLETE`，但必须保持 `EXTERNAL_VALIDATION_PENDING`。


> 文档角色：领域边界、数据、状态机、接口与技术架构事实源  
> 版本：1.0.0  
> 状态：APPROVED  
> 上位约束：BOOK-01、BOOK-02、BOOK-03

---

## 1. 架构目标

系统必须同时满足：

1. 支撑批量、长耗时、可取消的视频分析；
2. 业务事实、AI 运行结果和人工判断可追溯；
3. Java 后端承担事务、权限、状态、幂等和审计；
4. Python 承担媒体处理、模型推理和评测；
5. 模型、检测器和阈值可替换并可版本化；
6. 单个模型或 Worker 失败不破坏整个批次；
7. 保持个人/小团队可完成，不提前微服务化；
8. 与 SourceLens-AIOS 运行时完全解耦。

---

## 2. 总体架构

```text
┌─────────────────────────────────────────────────────┐
│ frameflow-web                                       │
│ Next.js / React / TypeScript                        │
│ Project · Profile · Batch · Matrix · Review · Select│
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS / REST / SSE
┌──────────────────────▼──────────────────────────────┐
│ Java Control & Business Plane                       │
│ Spring Boot 模块化单体                              │
│                                                     │
│ identity · project · batch · analysis · review      │
│ governance                                          │
└──────────────┬──────────────┬───────────────┬───────┘
               │              │               │
         PostgreSQL       Object Storage   RabbitMQ
               │              │               │
               └──────────────┬┴───────────────┘
                              │
┌─────────────────────────────▼───────────────────────┐
│ frameflow-ai-worker                                 │
│ Python                                               │
│ FFmpeg · OpenCV · ASR · OCR · Embedding · MLLM      │
│ Detector Registry · Pipeline Executor · Eval         │
└──────────────────────────────────────────────────────┘
```

首期部署单元只有：

```text
1 个 Java 应用
1 个 Web 应用
1 个 Python Worker 镜像（可运行多个副本）
1 个 PostgreSQL
1 个 S3 兼容对象存储
1 个 RabbitMQ
```

这不是微服务体系。Java 与 Python 分进程是语言/运行时边界，不代表需要 Spring Cloud、服务注册中心或 API Gateway。

---

## 3. 代码仓库结构

继续采用源码单仓库：

```text
FrameFlow/
├── frameflow-app/                 # Java 启动应用
├── frameflow-modules/
│   ├── identity/
│   ├── project/
│   ├── batch/
│   ├── analysis/
│   ├── review/
│   └── governance/
├── frameflow-shared-kernel/       # 极小共享类型，不放业务模型
├── frameflow-ai-worker/           # Python
│   ├── src/frameflow_ai/
│   ├── tests/
│   ├── pyproject.toml
│   └── Dockerfile
├── frameflow-web/                 # Next.js
├── contracts/
│   ├── openapi/
│   ├── messages/
│   ├── quality/
│   └── evaluation/
├── infra/
│   ├── compose/
│   └── local/
├── docs/
│   ├── 00-core/
│   ├── adr/
│   ├── runbooks/
│   └── 90-archive/
├── scripts/
└── evidence/
```

`docs/05-engineering/agent-control`、Task Capsule 等通用 Agent 控制内容不再属于 FrameFlow 产品文档。SourceLens-AIOS 所需任务输入放在独立的 `.sourcelens/` 或 SourceLens 工作区，不作为产品事实源。

---

## 4. 技术基线决策

## 4.1 Java

目标基线：

```text
JDK 21 LTS
Spring Boot 4.1.x
Maven 3.9+
Spring Security
Spring MVC
MyBatis-Plus Boot 4 Starter / MyBatis XML
Flyway
PostgreSQL
springdoc 3.x
JUnit 5
Testcontainers
ArchUnit
```

当前仓库是 JDK 17 + Spring Boot 3.4.5。转向阶段必须先执行兼容性 Spike：

1. 在独立分支升级；
2. 现有 M01 测试全部通过；
3. OpenAPI Diff 无未经批准的变化；
4. JWT、Jackson、MyBatis、Flyway 和 Testcontainers 无不可接受 workaround；
5. 失败可完整回滚。

通过后采用新基线；未通过不得在产品功能中混用两个 Boot 主版本，必须提交 ADR 决定受支持的替代版本。

## 4.2 Python

首期基线：

```text
Python 3.12
pyproject.toml + lockfile
FastAPI 仅用于健康/管理接口（可选）
Pydantic
RabbitMQ Client
FFmpeg/ffprobe
OpenCV
NumPy
Provider SDK 通过 Adapter 隔离
pytest
```

Worker 的核心不是 Web API，而是消息驱动的 Pipeline Executor。

## 4.3 Web

```text
TypeScript
React
Next.js App Router + BFF
TanStack Query
Tailwind CSS
shadcn/ui
openapi-typescript
播放器优先使用浏览器原生 video + 自定义时间轴
```

精确版本在前端 Bootstrap Task 中锁定；浏览器不直接持有 Refresh Token。

## 4.4 基础设施

```text
PostgreSQL 16（沿用当前 Compose，升级需单独决策）
MinIO（开发）/ S3 兼容对象存储（部署）
RabbitMQ
Docker Compose
GitHub Actions
OpenTelemetry / Micrometer（逐步引入）
```

首期不引入：

```text
Redis
Kafka
Elasticsearch
Nacos
Spring Cloud Gateway
Kubernetes
Istio
```

---

## 5. Java 模块边界

## 5.1 identity

负责：

- User；
- Team；
- TeamMember；
- 登录与 Token；
- 团队角色；
- 团队级授权。

沿用现有代码，但团队角色迁移为：

```text
OWNER / OPERATOR / REVIEWER / VIEWER
```

不负责 Project、Batch 或 Candidate 权限事实。

## 5.2 project

负责：

- Project；
- Project Reference；
- Quality Profile；
- Quality Profile Version；
- Brief Snapshot；
- Project 归档。

不负责视频文件、分析运行或人工 Finding 处置。

## 5.3 batch

负责：

- Batch；
- Candidate；
- 上传会话；
- 对象存储引用；
- Candidate 技术清单；
- 批次生命周期；
- Candidate 与生成元数据；
- 重新上传和父候选关系。

## 5.4 analysis

负责：

- Analysis Run；
- Stage Job；
- Detector Registry Snapshot；
- Finding；
- Finding Evidence；
- Score Vector；
- Model Invocation；
- Pipeline 版本；
- 机器 Decision；
- 消息发布和结果幂等。

Python Worker 不拥有这些业务事实表。

## 5.5 review

负责：

- Finding Disposition；
- Candidate Human Decision；
- Pairwise Preference；
- Ranking Snapshot；
- Cluster；
- Selection Set；
- 报告导出。

## 5.6 governance

负责：

- Audit Log；
- Usage Ledger；
- 站内 Notification；
- Retention Job；
- Provider/Detector 配置引用；
- 安全事件。

不要把所有“共享能力”都塞入 governance。模块业务规则仍由各自模块拥有。

---

## 6. 模块依赖规则

允许：

```text
app → all modules
review → analysis public application API
analysis → batch/project public query port
batch → project public query port
project → identity membership port（只做必要团队校验）
```

禁止：

- 跨模块调用 Mapper/Repository；
- 跨模块直接读取物理表；
- shared-kernel 放入通用 Entity；
- Controller 直接访问 Repository；
- Python Worker 直接更新 PostgreSQL 业务表；
- Web 直接访问对象存储永久凭据；
- AI Provider 回调直接改变最终 Candidate/Selection 状态。

ArchUnit 必须验证核心依赖方向。

---

## 7. 核心实体与数据表

## 7.1 现有身份表

保留：

```text
users
teams
team_members
refresh_token_sessions
idempotency_records
```

角色迁移通过 forward-only Flyway 完成，不重写 V2 历史。

## 7.2 Project

```text
projects
- id
- team_id
- name
- domain_template
- status ACTIVE/ARCHIVED
- default_quality_profile_id
- created_by
- created_at / updated_at
- version
```

约束：所有项目资源可追溯到 team_id。

## 7.3 Quality Profile

```text
quality_profiles
- id
- project_id nullable（团队模板可为空）
- team_id
- name
- status
- created_by

quality_profile_versions
- id
- quality_profile_id
- version_no
- schema_version
- config_json
- status DRAFT/PUBLISHED/RETIRED
- content_hash
- published_by / published_at
- created_at
```

约束：

- unique(quality_profile_id, version_no)；
- PUBLISHED config_json 不可更新；
- Batch 只引用具体 version ID。

## 7.4 Batch

```text
batches
- id
- project_id
- quality_profile_version_id
- name
- prompt_snapshot_json
- budget_mode
- max_external_calls
- max_estimated_cost
- target_top_k
- status
- created_by
- started_at / completed_at
- version
```

状态见第 9 节。

## 7.5 Candidate

```text
candidates
- id
- batch_id
- parent_candidate_id nullable
- external_id nullable
- source_type UPLOAD/API/REFERENCE
- object_key
- object_version nullable
- sha256
- original_filename
- media_type
- size_bytes
- generator_metadata_json
- ingest_status
- created_at
```

Candidate 文件不可原地替换。新文件创建新 Candidate；可用 parent_candidate_id 表示修复/再生成关系。

## 7.6 Media Derivative

```text
media_derivatives
- id
- candidate_id
- derivative_type PROXY/AUDIO/THUMBNAIL/KEYFRAME/CLIP/OCR_INPUT
- object_key
- source_sha256
- derivation_version
- start_ms / end_ms nullable
- metadata_json
- created_at
```

unique(candidate_id, derivative_type, derivation_version, start_ms, end_ms) 的实际形式由数据字典定稿。

## 7.7 Analysis Run

```text
analysis_runs
- id
- batch_id
- candidate_id nullable（批次级运行可为空）
- pipeline_version
- profile_version_id
- status
- decision
- started_at / completed_at
- estimated_cost / actual_cost
- cancellation_requested_at
- result_version
```

一次重跑创建新 Run，不覆盖旧 Run。

## 7.8 Stage Job

```text
analysis_stage_jobs
- id UUID
- analysis_run_id
- stage_type
- attempt_no
- status QUEUED/DISPATCHED/RUNNING/SUCCEEDED/FAILED/CANCELLED
- idempotency_key
- input_manifest_ref
- output_manifest_ref
- worker_id
- lease_expires_at
- error_category
- retry_at
- created_at / updated_at
```

unique(analysis_run_id, stage_type, attempt_no)。

## 7.9 Finding

```text
findings
- id
- analysis_run_id
- candidate_id
- rule_id / rule_version
- detector_id / detector_version
- category
- severity
- confidence
- start_ms / end_ms
- summary
- explanation
- suggested_action
- automation_eligibility
- created_at
```

```text
finding_evidence
- id
- finding_id
- evidence_type
- object_key nullable
- payload_json nullable
- start_ms / end_ms
- source_hash
```

## 7.10 Score / Ranking

```text
score_vectors
- analysis_run_id
- dimension
- score
- confidence
- source_detector

ranking_snapshots
- id
- batch_id
- config_hash
- pipeline_version
- status
- created_at

ranking_items
- ranking_snapshot_id
- candidate_id
- rank_no
- rank_score
- diversity_penalty
- explanation_json
```

## 7.11 Human Review

```text
finding_dispositions
- id
- finding_id
- reviewer_id
- disposition CONFIRM/DISMISS/EDIT
- revised_severity nullable
- revised_start_ms / revised_end_ms nullable
- reason_code
- note
- created_at

candidate_decisions
- id
- candidate_id
- reviewer_id
- decision KEEP/REJECT/REVIEW_LATER
- reason_code
- created_at

pairwise_preferences
- id
- batch_id
- candidate_a_id
- candidate_b_id
- outcome A/B/TIE
- reason_tags
- reviewer_id
- created_at
```

## 7.12 Selection

```text
selection_sets
- id
- batch_id
- version_no
- status DRAFT/LOCKED/EXPORTED
- ranking_snapshot_id nullable
- locked_by / locked_at

selection_items
- selection_set_id
- candidate_id
- position
- note
```

LOCKED 后不更新 items，只创建新版本。

## 7.13 Governance

```text
audit_logs
usage_ledger
notifications
model_invocations
outbox_messages
```

`model_invocations` 不保存 Provider 密钥。原始输入/输出按数据政策存对象存储，数据库只保存引用和摘要。

---

## 8. 关键不变量

1. Batch 引用具体 Quality Profile Version 和 Brief Snapshot。
2. Candidate 的 sha256、对象引用和源文件不可修改。
3. Analysis Run 绑定 pipeline/profile/detector/model 版本。
4. Finding 不被人工处置覆盖。
5. AUTO_REJECT 必须能追溯到 ACTIVE 且有资格的 Rule。
6. Analysis Error 永远不能被解释为质量 FAIL。
7. Selection Set LOCKED 后不可原地修改。
8. 同一个 Stage 结果以 jobId + attempt + resultId 幂等。
9. 迟到结果不能覆盖已取消 Run 或更高 result_version。
10. 所有跨 Team 资源访问返回不可枚举的 404。
11. 预算达到上限后不能由 Worker 自行增加调用。
12. Project ARCHIVED 后默认只读，但 Retention Job 可按政策清理派生物。

---

## 9. 状态机

## 9.1 Quality Profile Version

```text
DRAFT → PUBLISHED → RETIRED
```

禁止：PUBLISHED → DRAFT；PUBLISHED 内容更新。

## 9.2 Batch

```text
DRAFT → INGESTING → READY → ANALYZING → REVIEWING → COMPLETED
```

允许：

```text
INGESTING → READY（至少一个有效 Candidate）
INGESTING → FAILED（无有效 Candidate 且不可恢复）
ANALYZING → PARTIAL
ANALYZING → FAILED
ANALYZING → REVIEWING
DRAFT/INGESTING/READY/ANALYZING → CANCELLED
PARTIAL → REVIEWING
REVIEWING → COMPLETED
```

`COMPLETED` 只表示本次批次流程完成，不表示所有视频“合格”。

## 9.3 Analysis Run

```text
QUEUED → RUNNING → SUCCEEDED
                  → PARTIAL_FAILED
                  → FAILED
                  → CANCELLED
```

终态不可回到 RUNNING；重跑新建 Run。

## 9.4 Stage Job

```text
QUEUED → DISPATCHED → RUNNING → SUCCEEDED
                               → RETRY_WAIT → QUEUED
                               → FAILED
                               → CANCELLED
```

达到 maxAttempts 后进入 FAILED/DLQ。

## 9.5 Selection Set

```text
DRAFT → LOCKED → EXPORTED
```

---

## 10. 对象存储设计

对象路径不得使用用户原始文件名作为完整路径：

```text
team/{teamId}/project/{projectId}/batch/{batchId}/candidate/{candidateId}/raw/{sha256}
team/{teamId}/project/{projectId}/batch/{batchId}/candidate/{candidateId}/derived/{derivationVersion}/{type}/{id}
team/{teamId}/project/{projectId}/batch/{batchId}/reports/{selectionSetId}/{version}
```

规则：

- 数据库保存 object key，不保存永久公开 URL；
- 浏览器使用短期预签名 URL；
- Worker 使用服务身份或短期凭据；
- 上传完成必须二次确认对象大小和 hash；
- 失败上传和孤儿对象有扫描/清理；
- 原始视频、代理视频、关键帧和模型输入设置不同保留期；
- 删除必须先标记、后异步清理并审计。

---

## 11. 异步任务与 RabbitMQ

## 11.1 引入时机

RabbitMQ 不在纯可行性 CLI 阶段引入；从批量 Web 垂直切片开始使用，因为此时已存在：

- 长耗时任务；
- Worker 重启；
- 重试；
- 并发批次；
- 取消；
- DLQ；
- 水平扩展需求。

## 11.2 Command Envelope

```json
{
  "messageId": "uuid",
  "messageType": "analysis.stage.requested.v1",
  "occurredAt": "timestamp",
  "traceId": "string",
  "jobId": "uuid",
  "analysisRunId": "uuid",
  "stageType": "SEMANTIC_ASSERTIONS",
  "attempt": 1,
  "inputManifestRef": "object-or-api-ref",
  "budget": {},
  "deadlineAt": "timestamp"
}
```

## 11.3 Result Envelope

```json
{
  "messageId": "uuid",
  "messageType": "analysis.stage.completed.v1",
  "occurredAt": "timestamp",
  "traceId": "string",
  "jobId": "uuid",
  "analysisRunId": "uuid",
  "attempt": 1,
  "status": "SUCCEEDED",
  "outputManifestRef": "object-ref",
  "usage": {},
  "worker": {}
}
```

## 11.4 可靠性规则

- Java 在数据库事务中创建 Stage Job 和 outbox message；
- Dispatcher 发布时使用 Publisher Confirm；
- Worker 手动 ACK；
- Worker 输出先写对象存储，再发布 Result；
- Java 按 messageId/jobId/attempt 幂等消费；
- 业务落库成功后 ACK Result；
- transient error 进入指数退避；
- permanent error 进入 DLQ；
- DLQ 只允许人工或受控任务重放；
- 消息只传引用和最小元数据，不传完整视频或大量帧。

不使用 Kafka。

---

## 12. Python Worker 设计

## 12.1 内部结构

```text
worker/
├── pipeline_executor.py
├── stage_registry.py
├── detectors/
│   ├── technical/
│   ├── visual/
│   ├── audio/
│   ├── semantic/
│   └── similarity/
├── providers/
│   ├── vision_language.py
│   ├── speech_to_text.py
│   ├── ocr.py
│   └── embedding.py
├── schemas/
├── media/
├── evaluation/
└── observability/
```

## 12.2 Detector 接口

每个 Detector 定义：

```text
id
version
supportedStage
inputSchema
outputSchema
costClass
latencyClass
requiredCapabilities
run(context) -> DetectorResult
```

Detector 不直接决定 Batch 状态；只返回结构化结果。

## 12.3 临时文件

- 每个 job 独立临时目录；
- 限制磁盘、CPU、内存和执行时间；
- 成功或失败后清理；
- 只允许访问当前 job 对象；
- 日志不得包含完整预签名 URL 和凭据。

## 12.4 AI 节点

- 结构化输出；
- 调用次数上限；
- Provider 超时；
- 输入大小预算；
- 可选重试一次；
- 无递归 Agent；
- 不保存隐藏 CoT；
- 返回 Evidence references。

---

## 13. API 资源目录

现有 `/auth`、`/teams` 保留，角色契约更新。

MVP 新增资源：

```text
/projects
/projects/{projectId}
/projects/{projectId}/quality-profiles
/quality-profiles/{profileId}/versions
/quality-profile-versions/{versionId}/publish

/projects/{projectId}/batches
/batches/{batchId}
/batches/{batchId}/upload-sessions
/batches/{batchId}/candidates
/batches/{batchId}/analysis-runs
/batches/{batchId}/start-analysis
/batches/{batchId}/cancel-analysis

/candidates/{candidateId}
/candidates/{candidateId}/playback
/candidates/{candidateId}/findings
/candidates/{candidateId}/decision

/findings/{findingId}/disposition
/batches/{batchId}/rankings
/batches/{batchId}/pairwise-preferences
/batches/{batchId}/selection-sets
/selection-sets/{selectionSetId}/lock
/selection-sets/{selectionSetId}/exports

/analysis-runs/{runId}/events   # SSE
```

每个 Stage 先写手工 OpenAPI，再实现；运行时 springdoc 只做差异校验。

---

## 14. 幂等与并发

必须使用 Idempotency-Key：

- 创建 Batch；
- 完成上传会话；
- 启动分析；
- 取消分析；
- 创建/锁定 Selection Set；
- 外部 API 导入 Candidate。

并发控制：

- Project/Profile/Batch 使用 version 乐观锁；
- 同一 Batch 同一 pipeline 只允许一个 ACTIVE Run，除非显式创建实验 Run；
- Quality Profile 发布使用唯一版本号；
- Selection Set 锁定必须验证候选仍属于批次；
- 结果消费按 result_version 或状态条件更新；
- 重复 Result 返回成功但不重复创建 Finding。

---

## 15. 安全与隐私

### 15.1 租户隔离

所有资源通过 team_id 追溯；资源级越权统一返回 404。

### 15.2 上传安全

- 限制文件数、大小、容器和 codec；
- 不信任扩展名；
- ZIP 防路径穿越和压缩炸弹；
- 对象写入隔离前缀；
- 代理生成在受限 Worker 中执行；
- 不执行媒体内嵌脚本或外部 URL。

### 15.3 Provider 数据边界

Quality Profile 或 Team Policy 必须声明：

```text
ALLOW_EXTERNAL_PROVIDER
LOCAL_ONLY
REDACT_FACES（后置）
RETENTION_DAYS
ALLOW_MODEL_IMPROVEMENT=false（默认）
```

发送外部 Provider 前明确显示：

- 发送哪些帧/音频/转录；
- Provider；
- 估算成本；
- 数据保留策略。

### 15.4 密钥

- 当前压缩包中的本地 PEM 不进入正式交付包；
- 真实私钥通过环境/Secret 管理；
- 测试使用动态生成密钥；
- Provider Key 不写日志、Evidence 或数据库明文。

### 15.5 审计

至少记录：

- 质量模板发布；
- 自动 BLOCK 开关变化；
- 分析启动/取消/重跑；
- 人工覆盖；
- Selection 锁定；
- 数据删除；
- Provider 改动。

---

## 16. 可观测性与成本

每个请求和异步 Job 关联：

```text
requestId
correlationId
traceId
batchId
analysisRunId
jobId
candidateId
```

指标：

- 上传成功率；
- Stage 队列深度；
- Job 延迟与执行时间；
- 每阶段失败率；
- Provider 延迟/限流/Schema 失败；
- 视频分钟处理量；
- 实际成本；
- Worker 临时磁盘；
- Finding 数量和类别；
- 人工推翻率。

日志必须结构化且脱敏。首期使用本地日志和 Actuator；出现诊断需要后再接 OpenTelemetry 后端，不把完整 ELK 当 MVP 前置。

---

## 17. 测试体系

### 17.1 Java

- Domain/state machine unit tests；
- Service transaction/idempotency tests；
- PostgreSQL/MinIO/RabbitMQ Testcontainers；
- API contract tests；
- OpenAPI diff；
- ArchUnit；
- cross-team security tests；
- late result/cancel/retry tests。

### 17.2 Python

- Detector unit tests；
- 固定媒体 fixture；
- Schema tests；
- FFmpeg command golden tests；
- Provider Fake/recorded response tests；
- Pipeline cancellation/retry tests；
- no-network CI tests。

### 17.3 AI Evaluation

AI 效果不放在普通单元测试中伪装稳定：

- CI 使用 Fake/recorded responses；
- 独立 Eval Job 调真实模型；
- 结果写评测数据库和报告；
- Provider 回归不阻塞普通代码提交，但阻塞自动 BLOCK 发布。

### 17.4 E2E

最小 E2E：

```text
登录
→ 创建 Project/Profile
→ 创建 Batch
→ 上传多个 Candidate
→ 启动分析
→ 收到进度
→ 查看 Finding
→ 人工决策
→ 锁定 Top-K
→ 导出报告
```

---

## 18. 部署演进

### Local Development

```text
Docker Compose：PostgreSQL + MinIO + RabbitMQ
Java 和 Python/Web 可本机运行
```

### Alpha

单机或少量容器：

```text
Web
Java
Python Worker x1
PostgreSQL
MinIO/S3
RabbitMQ
```

### Pilot

按真实瓶颈扩展 Worker 副本；数据库仍单一事实源。

### 后续

只有出现以下证据才评估 Kubernetes：

- 多 Worker 频繁扩缩容；
- 多环境部署；
- 单机恢复无法满足试点；
- 已有稳定镜像、探针和资源数据。

微服务拆分不是默认终点。

---

## 19. 架构变更门槛

以下变化必须 ADR：

- Java/Python 职责转移；
- PostgreSQL 之外新增事实库；
- RabbitMQ 替换；
- 对象存储语义变化；
- 自动 BLOCK 决策所有权变化；
- Provider 数据出域策略变化；
- 模块拆服务；
- Quality Profile Schema 主版本变化。

普通 Detector 新增不需要 ADR，只需 Registry、评测和 Rule 生命周期。

