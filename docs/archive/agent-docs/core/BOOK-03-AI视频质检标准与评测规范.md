# BOOK-03：FrameFlow Select AI 视频质检标准与评测规范

> **Autopilot Profile 1.0.0**：用户已通过 `AUTONOMOUS-EXECUTION-CONTRACT.md` 预先授权本地、可回退的任务与阶段接受。真实客户试点、市场验证、生产发布和法律结论仍不得由 Agent 代替。缺少真实媒体或 Provider 时允许先达到 `LOCAL_MVP_COMPLETE`，但必须保持 `EXTERNAL_VALIDATION_PENDING`。


> 文档角色：AI 质量定义、自动决策、排名与评测事实源  
> 版本：1.0.0  
> 状态：APPROVED  
> 上位约束：BOOK-01、BOOK-02

---

## 1. 为什么必须单独制定质量规范

“视频质量好”不是单一概念。FrameFlow Select 必须把质量拆成可定义、可检测、可标注和可解释的维度，否则会出现：

- 不同模型和 Agent 使用不同标准；
- 一个总分掩盖严重问题；
- 排名权重随实现者喜好漂移；
- 为了让当前模型通过而不断修改阈值；
- 自动淘汰缺少证据；
- 用户反馈无法转化为评测数据。

本规范将研究型视频生成评测中的多维思想，转化为面向批量内容生产的产品质量门禁。它不直接复制某个学术榜单，也不把研究指标等同于业务决策。

---

## 2. 核心质量模型

FrameFlow Select 使用三层结果模型：

```text
Layer A：Eligibility Gate
候选是否具备进入人工优选池的资格

Layer B：Quality Vector
候选在不同质量维度的表现

Layer C：Batch Selection
在一个批次内，如何去重并选出 Top-K
```

### 2.1 禁止的单分数模型

禁止：

```text
video_quality = 85
85 以上保留，85 以下淘汰
```

原因：

- 85 无法解释；
- 不同维度不能互相抵消；
- 严重 Logo 错误不能被高审美分抵消；
- 同一分数不适用于不同业务模板；
- 分数校准会随 Provider 和数据分布变化。

### 2.2 正确结果

```text
Decision: REVIEW_REQUIRED

Hard Gate:
- file_decodable: PASS
- duration: PASS
- aspect_ratio: PASS
- required_cta: FAIL (confidence 0.93, manual review only)

Quality Vector:
- prompt_alignment: 0.86
- temporal_consistency: 0.63
- visual_quality: 0.81
- product_visibility: 0.92
- audio_subtitle: 0.77

Findings:
- 00:07.1–00:08.4 logo deformation, MAJOR
- CTA not detected in final 3 seconds, MAJOR
```

---

## 3. 质量维度体系

## 3.1 Q1 文件完整性与技术规格

性质：确定性优先，可形成高置信度硬门禁。

检查：

- 文件存在、可读取；
- 容器和视频流可解析；
- 可完整解码；
- 时长范围；
- 宽高比；
- 分辨率；
- 帧率；
- 码率边界；
- 音频流存在性；
- 颜色空间与旋转元数据；
- 文件大小；
- 首尾黑帧；
- 长冻结；
- 长静音；
- 音频削波。

默认检测器：

```text
ffprobe
FFmpeg filters
基础音频统计
```

自动淘汰资格：高。仅在规则明确、工具成功且结果无歧义时。

## 3.2 Q2 视觉技术质量

检查：

- 模糊；
- 过曝/欠曝；
- 压缩和块效应；
- 局部严重噪声；
- 画面抖动；
- 不自然闪烁；
- 帧间异常突变；
- 文本可读性；
- 安全区域。

检测方法：

```text
传统图像/视频统计
无参考 VQA 模型
光流或帧差特征
OCR 置信度
```

自动淘汰资格：中。只有严重、稳定并经标注集校准的指标可以 BLOCK；其余 REVIEW 或 RANK_ONLY。

## 3.3 Q3 AI 生成结构瑕疵

检查：

- 人脸身份漂移；
- 人体/手部结构异常；
- 商品形状改变；
- Logo 和文字变形；
- 物体无因出现、消失或穿模；
- 主体数量异常；
- 背景结构崩坏；
- 局部纹理熔化；
- 边界或遮挡错误。

检测方法：

```text
目标/人脸跟踪
关键点或结构模型
OCR/Logo 跟踪
跨帧 Embedding 一致性
专用瑕疵模型
多模态模型解释
```

自动淘汰资格：MVP 默认低。多数结果先进入 REVIEW，直到独立数据上达到阈值。

## 3.4 Q4 时序与运动一致性

检查：

- 主体身份在时间上的一致性；
- 动作连续性；
- 运动平滑性；
- 摄像机运动是否符合要求；
- 闪烁；
- 速度和方向突变；
- 长时间故事/状态连贯性；
- 物体状态变化是否合理。

检测方法：

```text
光流
目标跟踪
跨帧特征
镜头分段
专用时序模型
多模态时序判断
```

自动淘汰资格：低到中。严重冻结和闪烁可由确定性规则拦截；复杂一致性默认人工复核。

## 3.5 Q5 音频、语音与字幕

检查：

- 是否有音轨；
- 静音或削波；
- 语音可懂度；
- ASR 转录；
- 必须文案是否说出；
- 字幕与语音一致；
- 字幕错字、乱码和截断；
- 字幕安全区域；
- 口型同步（后置）；
- 背景音乐与语音冲突。

检测方法：

```text
音频统计
ASR
OCR
字幕轨解析
时间对齐
口型模型（后置）
```

自动淘汰资格：缺少必需音轨、明确静音、明确缺少必需文案可具备；其他默认 REVIEW。

## 3.6 Q6 Prompt / Brief 一致性

检查：

- 必需主体；
- 属性，例如颜色、数量、服装；
- 动作；
- 场景；
- 运镜；
- 商品首次出现时间；
- 产品展示时长；
- 卖点；
- CTA；
- 禁止元素；
- 参考图一致性；
- 生成任务的其他显式要求。

必须将自然语言 Brief 先转换为结构化 Assertions，再逐条判断。

示例：

```text
原 Brief：前 3 秒展示白色防晒衣，结尾出现“立即购买”。

Assertions：
A1 subject=product、防晒衣、required=true
A2 product.color=white
A3 product.first_appearance_ms <= 3000
A4 text_or_speech contains "立即购买"
A5 A4 time_range within final 3000ms
```

自动淘汰资格：取决于 Assertion。时长、OCR 精确禁词可高；复杂视觉语义默认 REVIEW。

## 3.7 Q7 品牌与发布准备

检查：

- 品牌 Logo 是否存在且清晰；
- 竞品 Logo；
- 指定品牌颜色；
- 法定或业务免责声明；
- AI 生成披露需求提示；
- C2PA/Content Credentials 是否存在及可验证；
- 文件命名和交付格式。

边界：

- FrameFlow 提供风险提示，不给出最终法律结论；
- C2PA 证明的是可验证来源记录，不等于内容事实真实；
- 平台规则必须版本化，注明生效日期和适用地区；
- 平台政策变化不能修改历史分析结果，只能产生新规则版本。

## 3.8 Q8 审美与创意适配

检查：

- 画面吸引力；
- 构图；
- 色彩；
- 节奏；
- 开场 Hook；
- 商品清晰度；
- 叙事完整性；
- 目标受众适配。

性质：高度主观。

自动淘汰资格：无。只能用于排名和人工参考。

## 3.9 Q9 批次重复度与多样性

检查：

- 完全重复；
- 近重复；
- 相同镜头结构；
- 相同主体/动作但轻微变化；
- Selection Set 的视觉和内容多样性。

检测方法：

```text
文件哈希
感知哈希
视频/关键帧 Embedding
音频或转录相似度
聚类
```

重复本身通常不自动淘汰原始候选，而是：

- 标记 Cluster；
- 推荐代表候选；
- 限制 Top-K 同簇数量；
- 允许 Reviewer 覆盖。

---

## 4. Finding 标准

每个 Finding 必须包含：

```text
findingId
analysisRunId
candidateId
ruleId
detectorId + detectorVersion
category
severity
confidence
startMs / endMs（适用时）
spatialRegion（后置，可选）
evidenceRefs
summary
explanation
suggestedAction
automationEligibility
machineDecision
humanDisposition
```

### 4.1 Severity

| 级别 | 定义 | 示例 |
|---|---|---|
| BLOCKER | 无法使用或明确违反硬要求 | 文件损坏、比例错误、必需 CTA 明确缺失 |
| MAJOR | 显著降低可用性或存在明显生成瑕疵 | 主体漂移、Logo 变形、长闪烁 |
| MINOR | 可接受但值得修复 | 短暂字幕不同步、轻微模糊 |
| INFO | 满足项、提示或低风险信息 | 商品在 1.8 秒出现、检测到 C2PA |

Severity 表示业务影响，不等于模型置信度。

### 4.2 Confidence

置信度必须来自可说明的来源：

- 确定性规则：`1.0` 或离散 `DETERMINISTIC`；
- 模型概率：需校准；
- 多模态 Judge：不能把模型自报概率直接当真实置信度；应根据保留集历史准确率映射；
- 多检测器一致：可提高但不能简单相乘。

### 4.3 Evidence

证据类型：

```text
METADATA
FRAME
FRAME_PAIR
VIDEO_CLIP
AUDIO_CLIP
TRANSCRIPT
OCR_SPAN
REFERENCE_COMPARISON
MODEL_RATIONALE
```

`MODEL_RATIONALE` 不能单独支持 AUTO_REJECT，至少要有可查看的媒体或结构化检测结果。

---

## 5. 规则体系

## 5.1 Rule 类型

```text
HARD_CONSTRAINT
DETECTOR_THRESHOLD
SEMANTIC_ASSERTION
PROHIBITED_ASSERTION
RANKING_PREFERENCE
DIVERSITY_POLICY
```

## 5.2 Rule 生命周期

```text
DRAFT → VALIDATING → ACTIVE → RETIRED
```

- DRAFT：只能用于内部试跑；
- VALIDATING：进入评测集，不影响生产决策；
- ACTIVE：可被 Quality Profile 使用；
- RETIRED：不再用于新 Profile，历史结果保留。

## 5.3 自动化资格

```text
EXPERIMENTAL
REVIEW_ONLY
AUTO_BLOCK_ELIGIBLE
```

从 REVIEW_ONLY 升级到 AUTO_BLOCK_ELIGIBLE 必须满足：

1. 明确正负样本定义；
2. 独立保留集；
3. 达到指定 Precision/Recall；
4. 优质候选误淘汰率低于护栏；
5. Evidence 可供 Reviewer 检查；
6. 至少一次人工审查规则风险；
7. 失败和 Provider 不可用时不会默认为 FAIL。

## 5.4 Rule 版本

规则语义、阈值、检测器或证据要求变化都必须升版本。历史 Batch 仍引用旧版本。

---

## 6. 分析流水线

MVP 使用有向无环流水线，不使用开放式多 Agent 自循环。

```text
S0 Ingest
S1 Probe & Validate
S2 Derive Media
S3 Deterministic QA
S4 Specialized Analysis
S5 Semantic Assertions
S6 Batch Similarity
S7 Decision & Ranking
S8 Human Review Feedback
```

### 6.1 S0 Ingest

输入：上传对象。  
输出：SHA-256、对象引用、原始元数据。  
失败：Candidate `INVALID`，不进入模型分析。

### 6.2 S1 Probe & Validate

执行：

- ffprobe；
- 完整或采样解码；
- 流、时长、尺寸、FPS、音轨；
- 配置硬约束。

输出：Technical Manifest 和 Findings。

### 6.3 S2 Derive Media

生成：

- 低码率代理视频；
- 音频；
- 镜头边界；
- 关键帧；
- 缩略图；
- OCR 帧样本；
- 可选短 Clip。

派生物必须绑定源文件 hash 和 derivation version。

### 6.4 S3 Deterministic QA

执行：

- blackdetect；
- freezedetect；
- silencedetect；
- 音量/削波统计；
- 规格规则；
- 文件与文本的精确匹配。

这是最先形成稳定产品价值的阶段。

### 6.5 S4 Specialized Analysis

可插拔：

- 视频质量模型；
- 目标/人脸跟踪；
- OCR；
- ASR；
- Embedding；
- Logo；
- 视觉结构异常；
- 口型（后置）。

每个 Detector 独立记录版本、输入和失败。

### 6.6 S5 Semantic Assertions

使用多模态模型逐条判断结构化 Assertions，而不是要求模型“总体评价这个视频”。

正确 Prompt 结构：

```text
输入：
- 明确 Assertion
- 视频代理或分段帧
- ASR/OCR
- 参考图
- 严格输出 Schema

输出：
- PASS / FAIL / UNCERTAIN
- 支持时间范围
- 支持证据引用
- 简短解释
```

限制：

- 每条 Assertion 最大一次主判断和一次可选复核；
- 禁止模型自己添加新业务规则；
- 不保存隐藏思维过程；
- 只保存结构化结论和面向用户的短解释；
- Schema 校验失败重试一次，仍失败则 `UNCERTAIN`。

### 6.7 S6 Batch Similarity

计算：

- 完全重复；
- 视觉近重复；
- 转录近重复；
- 镜头结构相似。

输出 Cluster，不修改单候选原始分数。

### 6.8 S7 Decision & Ranking

先门禁，再排名：

```text
Eligibility = gate(rules, findings, confidence, policy)
RankVector = quality_dimensions
RankScore = profile-specific weighted function
Selection = cluster-aware Top-K
```

### 6.9 S8 Human Feedback

人工决策进入独立表和评测流水线；不回写修改机器 Finding。

---

## 7. 自动决策模型

## 7.1 决策顺序

```text
分析失败？
  → ANALYSIS_ERROR 或 REVIEW_REQUIRED

存在确定性 BLOCK 失败？
  → AUTO_REJECT

存在高置信度、已获 AUTO_BLOCK_ELIGIBLE 的规则失败？
  → AUTO_REJECT

存在 MAJOR / UNCERTAIN / 模型冲突？
  → REVIEW_REQUIRED

无阻塞问题？
  → SHORTLIST_CANDIDATE
```

## 7.2 禁止行为

- 外部模型超时后默认 FAIL；
- 多模态模型一句“质量很差”触发 AUTO_REJECT；
- 用综合分数低于阈值直接淘汰；
- 缺少 Evidence 仍自动淘汰；
- 为达到淘汰率目标提高风险；
- 把相似度高等同于质量差。

## 7.3 人工覆盖

人工可以覆盖机器 Decision，但必须选择原因：

```text
FALSE_POSITIVE
FALSE_NEGATIVE
BUSINESS_EXCEPTION
CREATIVE_PREFERENCE
BAD_EVIDENCE
WRONG_SEVERITY
OTHER
```

覆盖不会删除原始结果。

---

## 8. 质量向量与排名

### 8.1 标准维度

所有分数规范化为 `[0,1]`，但只在同一版本管线和已校准范围内比较：

```text
technical_quality
visual_stability
temporal_consistency
prompt_alignment
product_visibility
brand_compliance
audio_subtitle_quality
aesthetic_fitness
```

`artifact_risk` 使用风险方向，聚合前转换或单独展示，避免方向混乱。

### 8.2 初始电商模板示例权重

```text
prompt_alignment        0.25
product_visibility      0.20
visual_stability        0.15
temporal_consistency    0.15
technical_quality       0.10
brand_compliance        0.10
audio_subtitle_quality  0.05
```

审美不在首版自动权重中，先作为参考。权重只是初始假设，必须用人工偏好校准。

### 8.3 惩罚

明确规则失败可以添加 ranking penalty，但 BLOCK 候选已不进入排名池。

```text
MAJOR confirmed finding: configurable penalty
MINOR finding: small penalty
UNCERTAIN: no hard penalty, increase review priority
```

### 8.4 批次多样性

Top-K 不能只按单候选分数取前 K。建议使用 Cluster 限制或 MMR 类目标：

```text
selection_gain(candidate)
=
quality_score
-
lambda * max_similarity(candidate, already_selected)
```

用户可以关闭多样性，但系统必须展示相似度影响。

### 8.5 排名解释

每个排名必须回答：

- 为什么比上一名低；
- 哪些维度贡献最大；
- 是否受相似度惩罚；
- 哪些分数来自不确定模型；
- 人工是否覆盖过。

---

## 9. 数据集与标注计划

## 9.1 数据集类型

### Development Set

用于快速开发，可持续变化，不用于最终 Gate。

### Calibration Set

用于阈值和置信度校准。与训练/Prompt 调试样本隔离。

### Gold Test Set

冻结版本，只用于阶段 Gate。禁止根据单个失败样本反复修改后立即在同一集合宣布提升。

### Pilot Set

真实用户数据，按明确授权使用；与内部公开演示数据隔离。

## 9.2 初始样本结构

可行性阶段最低：

```text
10 个 Prompt/Brief 组
每组至少 5 个候选
至少 3 种生成来源或质量条件
总计至少 50 条短视频
```

MVP 评测前扩展到：

```text
不少于 200 条候选
不少于 30 个 Prompt/Brief 组
包含技术失败、瑕疵、对齐失败和高质量正例
```

数量不是唯一标准；必须覆盖真实错误类型和困难负例。

## 9.3 数据切分

按以下维度隔离，避免泄漏：

- Prompt/Brief 组；
- Project；
- 生成模型/版本；
- 商品或角色；
- 近重复 Cluster。

同一近重复簇不能跨训练与测试集合。

## 9.4 标注单位

### Candidate 级

```text
KEEP / REVIEW / REJECT
总体可用性
Top-tier 标记
```

### Finding 级

```text
category
severity
startMs/endMs
是否真实
是否可操作
```

### Pairwise 级

```text
A better / B better / tie
原因标签
```

排名优先使用 Pairwise 与 Top-K 标签，不依赖不稳定的绝对 1～100 分。

## 9.5 标注质量

Gold Set：

- 至少 20% 样本双人独立标注；
- 分歧由第三方或项目所有者裁决；
- 记录标注指南版本；
- 统计一致性；
- 不能只由开发模型标注自己的测试集。

## 9.6 难例库

必须维护：

```text
严重漏检
高置信度误报
优质候选误淘汰
时间码错误
证据与结论不一致
相似度误聚类
Provider 版本回归
```

每次严重生产/试点错误都进入难例候选，但不能自动污染 Gold Set。

---

## 10. 离线评测指标

### 10.1 Finding 检测

- Precision / Recall / F1；
- 按 category 和 severity 分层；
- 严重问题 Recall；
- 高置信度 Finding Precision；
- 时间定位 IoU 或容差内命中率；
- Evidence 完整率。

### 10.2 自动门禁

- False Reject Rate；
- False Accept Rate；
- Good Candidate Retention；
- Auto-reject Coverage；
- 不确定率；
- Analysis Error Rate。

自动门禁优化顺序：

```text
False Reject 降低
→ GCR 达标
→ 严重问题 Recall
→ 自动覆盖率
```

### 10.3 排名

- Pairwise Accuracy；
- Kendall Tau / Spearman；
- NDCG@K；
- Recall@K of human top-tier；
- Cluster Diversity；
- Top-K overlap。

### 10.4 校准

- Reliability diagram；
- Expected Calibration Error（适用时）；
- 不同 Provider/类别的置信度分桶准确率。

模型自报 0.95 不等于系统置信度 0.95。

### 10.5 成本与性能

- 每候选成本；
- 每视频分钟成本；
- 每成功短名单成本；
- P50/P95 候选延迟；
- P50/P95 批次完成时间；
- 每阶段失败率；
- 缓存/复用节省。

---

## 11. 在线与试点评测

### 11.1 基线

试点前先记录人工原流程：

- 批次数量；
- 候选数量；
- 完整观看数量；
- 审核总时长；
- 最终保留数量；
- 人工问题分类。

### 11.2 试点指标

- FRR；
- GCR；
- 人工完整观看分钟减少；
- Finding 确认率；
- 人工覆盖率；
- 用户完成一个批次所需步骤；
- 单位成本；
- 用户愿意继续使用/付费的具体理由。

### 11.3 Shadow Mode

任何新自动 BLOCK 规则必须先运行 Shadow Mode：

```text
系统计算“将会淘汰”
但不影响用户候选池
收集人工结果
达到门槛后再激活
```

---

## 12. Provider 与模型评估

每个 Provider Adapter 必须记录：

```text
provider
model
modelVersion（可获得时）
requestSchemaVersion
promptTemplateVersion
inputMediaRefs
latency
usage
estimatedCost
responseHash
finishReason
errorCategory
```

### 12.1 选择原则

不因模型榜单高就直接采用。必须在 FrameFlow 自有数据上比较：

- 业务维度准确率；
- 时间码能力；
- 结构化输出稳定性；
- 成本；
- 延迟；
- 数据政策；
- 地区可用性；
- 限流和故障行为。

### 12.2 版本变化

Provider 未通知的模型变化可能引起漂移，因此：

- 定期跑固定 Canary Set；
- 发现明显回归时停止自动 BLOCK；
- 新版本先 Shadow；
- 分析结果固定实际模型标识和日期。

### 12.3 多模型复核

MVP 不做无限模型投票。仅允许在高价值、低置信度断言上使用一次独立复核，并设成本上限。

---

## 13. 失败与降级语义

| 失败 | 产品行为 |
|---|---|
| ffprobe/解码失败 | INVALID 或 AUTO_REJECT，附直接证据 |
| 派生媒体失败 | ANALYSIS_ERROR，可重试 |
| 专用模型失败 | 该维度 UNKNOWN，继续其他阶段 |
| 多模态 Provider 超时 | Semantic Assertions UNKNOWN，不自动 FAIL |
| JSON Schema 失败 | 重试一次；仍失败则 UNKNOWN |
| 部分候选失败 | Batch PARTIAL，成功结果可审查 |
| 排名失败 | 保留 Finding 和 Gate，排序标记不可用 |
| 成本达到预算 | 停止高成本阶段，未完成项 REVIEW_REQUIRED |
| 取消 | 不启动新 Stage，正在运行的 Stage 尽快终止或忽略迟到结果 |

迟到结果必须按 Analysis Run 状态和 job attempt 校验，不能覆盖已取消或新版本结果。

---

## 14. 成本控制

每个 Quality Profile 选择预算模式：

### ECONOMY

- 全部确定性检查；
- 低成本抽帧；
- 只对疑似或 Top 候选调用多模态模型。

### BALANCED

- 全部确定性与专用模型；
- 所有候选执行核心语义 Assertions；
- 疑似 Finding 复核一次。

### QUALITY

- 更密集抽帧/分段；
- 更多 Assertions；
- 高价值候选可独立复核。

每批次必须设硬预算：

```text
maxExternalCalls
maxEstimatedCost
maxProcessingMinutes
```

达到预算不得自动扩充调用。

---

## 15. 可解释性标准

面向用户的解释必须：

- 与具体规则对应；
- 不使用隐藏思维链；
- 不声称模型无法证明的事实；
- 区分“检测到”“推断”“不确定”；
- 给出可执行建议；
- 避免冗长通用描述。

示例：

```text
不好：这个视频整体质量不佳，建议优化。

好：00:07.1–00:08.4 商品包装上的品牌文字由“FRAME”变为“FRANE”；
违反规则 BR-LOGO-001。检测依据为连续 12 帧 OCR 与参考 Logo 对比。
建议重新生成该片段或替换镜头。
```

---

## 16. 评测版本与发布

每次对外声明效果必须绑定：

```text
datasetVersion
qualityProfileVersion
pipelineVersion
detectorVersions
provider/model versions
metricDefinitionVersion
runDate
sampleCount
limitations
```

禁止把不同版本的数据和指标混在一张表中。

---

## 17. 初始 Gate 阈值

这些阈值是启动基线，不是营销承诺。

### Feasibility Gate

- 至少 50 条候选、10 个 Prompt/Brief 组；
- 至少三类高价值问题在独立样本上达到 Precision ≥ 0.75；
- 严重问题 Recall ≥ 0.70；
- Prompt/Brief 关键 Assertion 判断准确率或 pairwise agreement ≥ 0.70；
- 证据完整率 ≥ 0.95；
- 无自动 BLOCK 由纯模型理由单独触发。

### MVP Evaluation Gate

- Gold Set 至少 200 条、30 个组；
- Good Candidate Retention ≥ 0.90；
- False Reject Rate ≤ 0.05；
- Top-K Recall of human top-tier ≥ 0.85；
- 严重技术问题 Recall ≥ 0.90；
- Batch 分析成功或可用部分成功率 ≥ 0.95；
- 所有 AUTO_REJECT 都有直接证据。

### Pilot Gate

- 人工完整观看量减少 ≥ 50%；
- 优质候选损失 ≤ 5%；
- Finding 人工确认率 ≥ 60%；
- 分析成本不超过估算人工审核价值的 25%；
- 至少 3 个真实批次完整走通。

如真实数据表明阈值不合理，必须通过 BOOK-01 的变更流程修订，且不能追溯性修改过去 Gate 结论。

---

## 18. 研究与产品边界

可以研究：

- 新 VQA/Artifact Detector；
- Pairwise Ranker；
- 多模态 Judge；
- Prompt decomposition；
- 相似度和 Selection 优化。

但研究结果进入产品前必须满足：

```text
可复现
可版本化
可评测
可降级
有成本边界
有失败语义
```

论文指标或公开 Benchmark 只能用于技术选择，不能替代 FrameFlow 自有业务评测。

