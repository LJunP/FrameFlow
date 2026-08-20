# BOOK-02：FrameFlow Select 产品与用户体验蓝图

> **Autopilot Profile 1.0.0**：用户已通过 `AUTONOMOUS-EXECUTION-CONTRACT.md` 预先授权本地、可回退的任务与阶段接受。真实客户试点、市场验证、生产发布和法律结论仍不得由 Agent 代替。缺少真实媒体或 Provider 时允许先达到 `LOCAL_MVP_COMPLETE`，但必须保持 `EXTERNAL_VALIDATION_PENDING`。


> 文档角色：产品需求、用户流程与体验事实源  
> 版本：1.0.0  
> 状态：APPROVED  
> 上位约束：BOOK-01

---

## 1. 产品目标

FrameFlow Select 将一批 AI 生成短视频转换为一个**可解释、可复核、去重后的候选短名单**。

产品不是让用户和模型聊天，而是让用户完成一个明确工作：

```text
创建质检标准
→ 导入一批候选视频
→ 自动分析
→ 查看淘汰、复核和短名单
→ 核对问题证据
→ 完成人工判断
→ 锁定 Top-K
→ 导出报告或进入下一流程
```

---

## 2. 首要用户与 Jobs-to-be-Done

### 2.1 团队所有者 OWNER

**场景**：负责 AI 内容团队效率、成本和交付质量。

核心任务：

- 管理团队和成员；
- 查看批次处理量、成本和成功率；
- 配置默认质量模板；
- 决定哪些规则可以自动淘汰；
- 查看误淘汰、人工推翻和模型表现；
- 锁定最终选择集。

成功标准：

- 知道系统节省了多少审核时间；
- 知道自动决策是否可信；
- 能控制预算、数据保留和 Provider。

### 2.2 内容操作员 OPERATOR

**场景**：批量生成或收集候选视频，需要尽快完成初筛。

核心任务：

- 创建项目和批次；
- 选择质量模板；
- 填写 Prompt、商品、文案和规格；
- 批量上传或通过 API 导入视频；
- 启动、取消或重跑分析；
- 处理上传失败和分析失败；
- 把候选交给 Reviewer。

成功标准：

- 无需逐条打开视频即可知道处理进度；
- 能快速定位失败原因；
- 一批视频能够重复、幂等地处理。

### 2.3 质量审核员 REVIEWER

**场景**：负责内容质量、客户交付或投放前终审。

核心任务：

- 按系统分类和严重度筛选；
- 在播放器时间轴查看 Finding；
- 接受、驳回或修改机器判断；
- 两两比较候选；
- 标记保留、待修复和淘汰；
- 从相似候选中保留代表；
- 生成 Top-K 选择集。

成功标准：

- 只完整观看少量高价值候选；
- 每个自动结论都有证据；
- 误报可以快速纠正且反馈被保留。

### 2.4 查看者 VIEWER

**场景**：客户、业务负责人或只读协作者。

核心任务：

- 查看已授权项目、批次和报告；
- 查看短名单与问题证据；
- 不修改规则、状态和最终选择。

---

## 3. MVP 角色模型

团队角色锁定为：

```text
OWNER / OPERATOR / REVIEWER / VIEWER
```

| 操作 | OWNER | OPERATOR | REVIEWER | VIEWER |
|---|---:|---:|---:|---:|
| 管理团队与角色 | ✅ | ❌ | ❌ | ❌ |
| 创建项目 | ✅ | ✅ | ❌ | ❌ |
| 创建/修改质量模板草案 | ✅ | ✅ | ❌ | ❌ |
| 发布质量模板版本 | ✅ | ❌ | ❌ | ❌ |
| 创建批次和上传候选 | ✅ | ✅ | ❌ | ❌ |
| 启动/取消/重跑分析 | ✅ | ✅ | ❌ | ❌ |
| 查看全部分析结果 | ✅ | ✅ | ✅ | ✅ |
| 确认/驳回 Finding | ✅ | ❌ | ✅ | ❌ |
| 人工淘汰/保留候选 | ✅ | ❌ | ✅ | ❌ |
| 锁定 Selection Set | ✅ | ❌ | ✅ | ❌ |
| 修改自动淘汰阈值 | ✅ | ❌ | ❌ | ❌ |
| 查看成本和模型统计 | ✅ | 可查看自己的批次 | 可查看项目级 | ❌ |

权限必须在后端校验；前端隐藏按钮不是授权机制。

---

## 4. 业务对象的用户含义

### 4.1 Project

一个持续的内容目标，例如：

```text
夏季防晒衣短视频广告
新品手机 AI 素材测试
某数字人账号 8 月内容
```

Project 保存稳定上下文：品牌、商品、受众、常用限制和默认质量模板。

### 4.2 Quality Profile

一组可版本化的质检标准，回答：

```text
什么必须满足？
什么问题应当自动淘汰？
什么问题只提示人工？
哪些维度影响排名？
```

Quality Profile 发布后不可原地修改；修改生成新版本。

### 4.3 Batch

一批具有共同生成目的、Prompt 或 Brief 的候选视频。

Batch 是产品主工作单元。它固定引用：

- Project；
- Quality Profile Version；
- Prompt/Brief 快照；
- 分析管线版本；
- 排名配置版本。

### 4.4 Candidate

批次中的一个不可变视频候选。候选可以记录：

- 原始文件；
- 生成模型和模型版本；
- Seed；
- Prompt；
- 参考图；
- 外部生成任务 ID；
- 父候选或修复来源。

没有这些元数据时仍允许上传，但标记为 `UNKNOWN`，不能伪造。

### 4.5 Analysis Run

对一个候选或批次执行一次固定版本的分析。重跑不会覆盖旧结果。

### 4.6 Finding

一个可定位的问题或满足项。Finding 不等于最终裁决。

### 4.7 Ranking Snapshot

在固定配置和固定候选集合上计算的一次排名快照。候选、分数或权重变化后生成新快照。

### 4.8 Selection Set

最终人工确认的候选集合，例如 Top 10。锁定后只能创建新版本，不能直接覆盖。

---

## 5. 端到端主流程

## 5.1 创建项目

输入：

- 项目名称；
- 业务类型；
- 品牌/商品名称；
- 默认视频平台与比例；
- 可选参考素材；
- 默认 Quality Profile。

系统行为：

- 创建 Project；
- 记录创建人和 Team；
- 初始化审计日志；
- 不自动创建复杂客户、合同或支付对象。

验收：

- 跨团队不可访问；
- 重复请求可幂等；
- 项目可归档但不物理删除已分析数据。

## 5.2 创建质量配置

用户从模板复制：

```text
ECOMMERCE_SHORT_AD_V1
```

并配置：

- 允许时长；
- 宽高比、分辨率和帧率；
- 是否必须有音频；
- 商品首次出现截止时间；
- 必须出现的卖点和 CTA；
- 指定 Logo、颜色、禁用词；
- 生成瑕疵规则；
- 自动淘汰阈值；
- 排名权重；
- Top-K 数量或比例。

发布动作：

```text
DRAFT → PUBLISHED
```

一旦被 Batch 引用，该版本不可修改。

## 5.3 创建批次

必填：

- 批次名称；
- Project；
- Quality Profile Version；
- Prompt 或 Brief；
- 预期候选数量；
- Top-K 目标；
- 分析预算模式：`ECONOMY / BALANCED / QUALITY`。

可选：

- 参考商品图；
- 参考角色图；
- 指定生成模型；
- 外部批次 ID；
- 截止时间。

创建时必须生成不可变 Brief Snapshot，后续项目模板变化不影响该批次。

## 5.4 批量导入候选

MVP 支持：

1. 浏览器多文件上传；
2. ZIP 导入（服务端安全解包，限制文件数和路径）；
3. 预签名直传；
4. 简单 API 导入对象存储 URL（只允许受信源和受控域名）。

每个文件执行：

```text
扩展名与 MIME 初检
→ 大小限制
→ SHA-256
→ 去重检查
→ 对象存储写入
→ ffprobe 解码验证
→ Candidate READY 或 INVALID
```

相同 SHA-256 在同一 Team 内默认提示重复，不直接删除；用户可选择引用已有对象。

## 5.5 启动分析

启动前系统展示：

- 候选数量；
- 预计调用的检测阶段；
- 预算上限；
- 是否允许外部 AI Provider；
- 自动淘汰规则；
- 数据保留期限。

启动后 Batch 进入：

```text
READY → ANALYZING
```

分析过程必须支持：

- 查看总进度和每阶段进度；
- 取消尚未执行的任务；
- 失败重试；
- 只重跑某个阶段；
- 部分成功；
- 不重复计费和不重复写结果。

## 5.6 自动分析

用户看到的阶段：

```text
1. 文件与规格
2. 画面/音频预处理
3. 技术质量
4. AI 生成瑕疵
5. Prompt / Brief 对齐
6. 批次相似度与聚类
7. 综合门禁与排名
```

内部技术细节由 BOOK-03/04 定义。

## 5.7 初筛结果

系统将候选分为：

```text
AUTO_REJECT
REVIEW_REQUIRED
SHORTLIST_CANDIDATE
ANALYSIS_ERROR
```

`AUTO_REJECT` 只允许由已批准的高置信度硬规则触发。

结果总览必须显示：

- 每类数量和比例；
- 主要淘汰原因；
- 失败阶段；
- 预计节省的完整观看数量；
- 误淘汰风险提示；
- Top-K 初始推荐。

## 5.8 人工复核

Reviewer 可以按以下优先级工作：

1. 高价值但存在中置信度问题的候选；
2. 系统推荐的 Top-K；
3. 相似聚类中的代表候选；
4. AUTO_REJECT 抽样；
5. ANALYSIS_ERROR。

对每个 Finding，Reviewer 可以：

```text
CONFIRM
DISMISS
EDIT_SEVERITY
EDIT_TIME_RANGE
ADD_NOTE
```

对 Candidate，可以：

```text
KEEP
REVIEW_LATER
REJECT
```

所有人工决定必须记录操作者、时间、原因和原机器结果。

## 5.9 Top-K 选择

系统先提供自动排名和相似聚类，Reviewer 完成最终选择。

选择界面支持：

- 候选网格；
- 两两对比；
- 同步播放；
- 维度分数对比；
- 相似度提示；
- “替换为同簇更优候选”；
- 固定某个候选不参与自动重排。

锁定后生成 `Selection Set Version`。

## 5.10 导出

MVP 导出：

- JSON；
- CSV；
- 可打印 HTML/PDF 报告；
- 选中候选的短期下载链接；
- 机器 Finding 与人工决策明细。

MVP 不自动发布到平台。

---

## 6. 页面与信息架构

### 6.1 登录与团队

复用现有身份基座，完成：

- 注册、登录、刷新、登出；
- 团队切换；
- 成员和角色管理；
- 安全会话。

### 6.2 Dashboard

展示：

- 活跃项目；
- 最近批次；
- 分析中/失败/待复核数量；
- 本周期处理视频分钟；
- 人工观看减少率；
- 近期高频问题。

Dashboard 不做复杂 BI。

### 6.3 Project Detail

标签页：

```text
Overview
Quality Profiles
Batches
Reference Assets
Members（后置）
```

### 6.4 Quality Profile Builder

界面按规则类型分组：

```text
技术规格
Prompt/Brief 约束
品牌与文本
AI 生成瑕疵
音频与字幕
排名偏好
自动化策略
```

每条规则显示：

- 类型；
- 参数；
- 严重度；
- 自动淘汰资格；
- 所需检测器；
- 成本等级；
- 当前是否已有评测证据。

未通过评测的规则只能选择“提示”，不能选择“自动淘汰”。

### 6.5 Batch Create / Upload

必须支持：

- 清晰的上传限制；
- 可恢复上传；
- 失败文件重试；
- 外部 ID 和生成元数据；
- Prompt/Brief 快照预览；
- 启动前成本和范围确认。

### 6.6 Batch Processing

显示：

- 总进度；
- 各阶段进度；
- Worker 健康；
- 重试与失败；
- 已花费和预算剩余；
- 取消按钮；
- 事件时间线。

禁止展示无价值的模型“思维过程”。只展示结构化步骤、工具结果和失败原因。

### 6.7 Candidate Matrix

每个卡片显示：

- 缩略图和时长；
- 决策状态；
- 主要问题标签；
- 质量向量摘要；
- 排名；
- 相似簇；
- 人工状态。

过滤器：

```text
Decision
Severity
Finding Category
Score Range
Cluster
Review Status
Generator/Model
```

### 6.8 Candidate Review

核心布局：

```text
视频播放器 + 时间轴
左侧 Finding 列表
右侧 Brief/规则与证据
底部候选决策和备注
```

时间轴上区分：

- 技术问题；
- 视觉瑕疵；
- 音频/字幕；
- Brief 对齐；
- 人工标记。

### 6.9 Compare & Select

支持：

- A/B 对比；
- 同簇候选对比；
- 综合维度对比；
- 加入/移出 Selection；
- 排名解释；
- 多样性提醒。

### 6.10 Evaluation Dashboard（MVP 后半段）

展示：

- 不同 Pipeline/Provider 版本的指标；
- Finding 确认率；
- 误淘汰样本；
- Top-K 保留率；
- 单位成本和延迟；
- 失败率；
- 数据集版本。

这是产品可信度和 AI 学习主线，不是普通运维图表。

---

## 7. MVP 功能清单

### 7.1 必须包含

```text
现有身份/团队能力
项目
Quality Profile 版本
批次
批量上传
对象存储
候选不可变记录
异步分析任务
技术规格检查
黑帧/冻结/静音等确定性检查
抽帧、镜头切分与音频提取
Prompt/Brief 对齐的最小多模态判断
Finding + 时间码 + 关键帧证据
重复/近重复聚类
门禁分类
批次内排名
人工确认/驳回
Top-K Selection Set
JSON/CSV/HTML 报告
评测数据与最小 Dashboard
```

### 7.2 MVP 可降级

如果某些模型能力尚未达到 Gate，可以先以实验功能发布：

- 人体结构异常；
- 复杂物理错误；
- 口型同步；
- 高质量 Logo 形变；
- 审美评分；
- 长时剧情一致性。

降级方式是“只提示、不能自动淘汰”，而不是伪装准确。

### 7.3 MVP 明确不含

见 BOOK-01，不重复扩展。特别强调：

- 不自动生成视频；
- 不提供通用聊天入口；
- 不提供多 Agent 编排页面；
- 不自动发布；
- 不承诺内容合规结论；
- 不做付费计费实现，先记录 usage ledger。

---

## 8. Quality Profile 用户模型

一条规则至少包含：

```text
ruleId
name
category
ruleType
parameters
severity
actionPolicy
detectorRequirement
evidenceRequirement
confidenceThreshold
rankingWeight（适用时）
```

规则类型：

| 类型 | 用途 | 示例 |
|---|---|---|
| HARD_CONSTRAINT | 确定性规格 | 时长、比例、解码失败 |
| DETECTOR_THRESHOLD | 数值阈值 | 冻结超过 2 秒、静音超过 5 秒 |
| SEMANTIC_ASSERTION | 语义要求 | 商品前 3 秒出现、必须出现 CTA |
| PROHIBITED_ASSERTION | 禁止语义 | 不得出现竞品 Logo |
| RANKING_PREFERENCE | 排名偏好 | 商品可见性更高、动作更自然 |
| DIVERSITY_POLICY | 选择集多样性 | 同簇最多保留 2 条 |

动作策略：

```text
BLOCK
REVIEW
WARN
RANK_ONLY
```

仅 `BLOCK` 可触发 AUTO_REJECT，并且必须满足 BOOK-03 的证据与校准门槛。

---

## 9. 状态与用户可见语义

### 9.1 Project

```text
ACTIVE → ARCHIVED
```

不提供物理删除已产生分析结果的 Project。

### 9.2 Quality Profile Version

```text
DRAFT → PUBLISHED → RETIRED
```

PUBLISHED 不可编辑；RETIRED 仍可查看历史批次。

### 9.3 Batch

```text
DRAFT
→ INGESTING
→ READY
→ ANALYZING
→ REVIEWING
→ COMPLETED
```

旁路：

```text
DRAFT/READY/ANALYZING → CANCELLED
INGESTING/ANALYZING → FAILED 或 PARTIAL
```

`PARTIAL` 表示至少一个候选成功且至少一个不可恢复失败，仍可进入 Review。

### 9.4 Candidate Decision

```text
UNDECIDED
AUTO_REJECT
REVIEW_REQUIRED
SHORTLIST_CANDIDATE
ANALYSIS_ERROR
```

人工结果单独记录：

```text
KEEP / REJECT / REVIEW_LATER
```

机器状态和人工结果不得共用一个字段覆盖。

### 9.5 Selection Set

```text
DRAFT → LOCKED → EXPORTED
```

LOCKED 后修改必须创建新版本。

---

## 10. 通知与协作

MVP 只做站内通知：

- 上传批次完成；
- 分析完成或部分失败；
- Batch 等待人工复核；
- Selection Set 已锁定；
- 预算接近上限。

不做自动外发邮件、短信和企业 IM。可通过 Webhook/API 后置扩展。

---

## 11. 搜索、筛选和分页

MVP 使用 PostgreSQL：

- Project 名称；
- Batch 名称；
- Candidate 外部 ID；
- Finding category；
- 状态、模型、时间和分数范围。

不引入 Elasticsearch。只有出现跨大量转录文本、OCR 文本和复杂中文搜索的真实需求时再评估。

---

## 12. 产品数据与反馈闭环

每次人工操作都形成结构化反馈：

```text
Finding 被确认或驳回
严重度被调整
时间码被调整
候选被 KEEP/REJECT
A/B 偏好
Selection Set 最终结果
```

反馈用途：

- 评估规则和模型；
- 校准阈值；
- 生成误报/漏报样本；
- 优化排序；
- 形成垂直质量模板。

用户数据默认不用于训练公开模型；产品自己的统计与评测使用必须经过数据政策和脱敏边界。

---

## 13. 试点用户体验目标

首个试点必须能回答：

1. 用户原来每批完整观看多少条、耗时多少？
2. FrameFlow 后完整观看多少条？
3. 系统是否保留了用户最终认为最好的候选？
4. 哪类问题最有价值？
5. 哪类问题误报最多？
6. 用户是否信任证据和时间码？
7. 用户愿意按处理分钟、批次或席位付费吗？

不允许只问“你觉得这个产品怎么样”。

---

## 14. 商业模式假设（不进入 MVP 实现）

可验证的商业模式：

### SaaS

```text
基础席位费
+
视频处理分钟额度
+
高质量模型/高级规则用量
```

### API

```text
按处理分钟或候选计费
```

### 私有化/本地部署（后置）

面向不允许素材出域的团队。只有出现真实客户和运维能力后评估。

MVP 只实现 usage ledger，不实现付款、发票或复杂套餐。

---

## 15. 产品验收主场景

### 场景 A：规格与明显故障

80 条 9:16 电商视频中混入：

- 16:9；
- 无法解码；
- 过短；
- 长冻结；
- 全程静音。

系统应精确定位并高置信度拦截。

### 场景 B：Brief 对齐

Brief 要求：

- 前 3 秒出现白色商品；
- 出现三个卖点；
- 最后 3 秒出现 CTA；
- 不得出现竞品 Logo。

系统应给出每条候选的满足/缺失项及证据，不允许只返回总体评价。

### 场景 C：生成瑕疵

候选出现主体漂移、文字扭曲、闪烁或物体消失。系统应定位时间段并交给 Reviewer，未经校准不得自动淘汰。

### 场景 D：重复聚类

100 条候选中有大量近重复内容。系统应按相似簇展示，并在 Selection 中避免 Top-K 全部来自同一簇。

### 场景 E：人工纠错

Reviewer 驳回机器 Finding 后，原始结果和人工结果都被保留，并进入评测样本候选。

### 场景 F：部分失败

某个外部模型超时。技术检查和其他候选结果仍可用，Batch 标记 PARTIAL，失败候选进入 REVIEW_REQUIRED/ANALYSIS_ERROR，不被误淘汰。

---

## 16. 产品发布口径

在不同 Gate 前，只允许使用以下措辞：

| 阶段 | 允许口径 |
|---|---|
| 可行性前 | 产品假设 / 原型 |
| 可行性通过 | 已验证若干质检维度可行 |
| 单批次闭环 | 可运行 Alpha |
| 评测门禁通过 | 可评测 Beta |
| 试点通过 | 已完成有限真实用户试点 |
| 未有规模证据 | 禁止写“生产级”“大规模”“企业级成熟” |

