# BOOK-02:产品与领域设计

## 1. 用户与角色
- 系统角色:OWNER / OPERATOR / REVIEWER / VIEWER。
- 外部用户:内容创作者、内容工作室成员、广告素材团队、MCN、AI 视频服务团队。

## 2. 领域实体(学习主线将逐个亲手实现)
- Project:项目容器,归属团队。
- Prompt / Brief:创作提示与企业规定的不可变快照。
- Quality Profile:质检标准集合,可版本化、可发布,历史批次引用旧版本(不可变)。
- Generation Batch:一批候选视频,绑定一个 Profile 版本与 Brief。
- Candidate:单条候选视频,含上传会话、对象元数据、版本。
- Analysis Run:一次分析执行,含状态机。
- Finding:证据化问题(规则、检测器、维度、类型、判定、严重度、置信度、时间码、证据、来源)。
- SimilarityCluster:近重复分组(代表候选)。
- Ranking Snapshot:固定一组分数与构成。
- SelectionSet:人工确认的 Top-K,可锁定(不可变)与导出。

## 3. 状态机(LR 阶段逐个落库)
- Candidate:READY 到 ANALYZING 到 ANALYZED / AUTO_REJECT / REVIEW_REQUIRED / ANALYSIS_ERROR / INVALID。
- Analysis Run:CREATED 到 RUNNING 到 SUCCEEDED / FAILED / CANCELLED。
- SelectionSet:DRAFT 到 LOCKED。
- 任何分析失败必须记录为 ANALYSIS_ERROR,不得伪装为视频不合格。

## 4. 质检维度(质量模型)
- 确定性:时长、宽高比、分辨率、帧率、黑帧、冻结、静音、无音频。
- 语义:Prompt/Brief 对齐、质量印象、语义违反;判定仅允许 PASS / VIOLATE / UNKNOWN / ERROR,UNKNOWN 不得当不合格。

## 5. 产品页面(学习主线 LR12 亲手实现)
登录、Dashboard、Project、Quality Profile、Batch、批量上传、处理进度、Candidate Matrix、视频播放器与时间码 Finding、相似度聚类、候选对比、Top-K、人工复核、Selection Set、导出。

## 6. 范围控制
MVP 与学习主线不引入:Kubernetes、Istio、Kafka、复杂微服务、Service Mesh、大型向量数据库集群、自动发布。

## 7. Evidence 契约
每个 Finding 必须携带机器可查证据:检测器 id+版本、规则语义、时间码或帧、原始输出引用;无证据不允许自动结论。
