# BOOK-06：FrameFlow Select 研发运行与防偏航手册

> **Autopilot Profile 1.0.0**：用户已通过 `AUTONOMOUS-EXECUTION-CONTRACT.md` 预先授权本地、可回退的任务与阶段接受。真实客户试点、市场验证、生产发布和法律结论仍不得由 Agent 代替。缺少真实媒体或 Provider 时允许先达到 `LOCAL_MVP_COMPLETE`，但必须保持 `EXTERNAL_VALIDATION_PENDING`。


> 版本：1.0.0  
> 日期：2026-08-20  
> 权威范围：人和 Agent 的工作方式、任务控制、独立验证、变更管理、防自循环、防过度治理与学习真实性  
> 上位文档：[BOOK-01 项目宪章](./BOOK-01-项目宪章与权威地图.md)  
> 阶段依据：[BOOK-05 开发路线图](./BOOK-05-开发路线图与阶段门禁.md)

---

## 1. 手册目标

本手册用于防止项目在长期开发中发生以下重大事故：

1. **产品偏航**：从 AI 视频批量质检与优选滑向视频生成、剪辑、通用审核或通用 Agent 平台；
2. **Agent 自循环**：Agent 不断分析、改写、复审和再规划，却没有产生可验收用户结果；
3. **过度治理**：为了控制开发而持续创建 Schema、Prompt、角色、审批和文档，使治理成本超过产品开发；
4. **技术简历化**：为了覆盖技术名词引入微服务、Kafka、Kubernetes、Istio，而不是解决真实瓶颈；
5. **指标投机**：修改阈值、测试或数据集，使评测看起来通过；
6. **学习空心化**：项目代码快速增长，但项目所有者无法解释、调试或重建核心能力；
7. **双平台重叠**：FrameFlow Select 与 SourceLens-AIOS 重复建设 Agent Infra。

核心原则：

> **把 Agent 当作受约束的研发执行者，而不是产品方向和完成标准的最终裁决者。**

---

## 2. 最终决策权

### 2.1 产品所有者

项目所有者保留以下不可委托的最终权力：

- 产品目标与非目标；
- 首要用户和首个垂直场景；
- North Star 和护栏指标；
- Stage Gate 是否通过；
- L2/L3 变更；
- 数据、隐私和风险边界；
- 重大依赖与架构选择；
- 合并到 `main`；
- 对外发布；
- 终止、回退或改变项目。

Agent 可以提交建议，但不能以“多数 Agent 一致”替代产品所有者决定。

### 2.2 不可由同一执行者同时承担的职责

以下职责必须分离：

```text
实现
≠
最终验收
```

```text
模型/Prompt 调优
≠
Gold Test Set 结论批准
```

```text
业务规则提出
≠
自动淘汰权限批准
```

```text
代码修改
≠
生产发布批准
```

个人项目可以由同一个自然人最终承担多个职责，但必须在不同步骤、不同证据基础上完成；不能把实现时的主观判断直接视为独立验证。

---

## 3. SourceLens-AIOS 与 FrameFlow 的开发关系

### 3.1 允许的关系

SourceLens-AIOS 可以：

- 接收 FrameFlow Task；
- 为 Agent 生成受控工作区；
- 限制文件、命令、网络和预算；
- 调度实现 Agent 与 Reviewer Agent；
- 保存执行轨迹和 Evidence；
- 提供回滚和恢复；
- 作为 FrameFlow 的 dogfooding 开发工具。

### 3.2 禁止的关系

FrameFlow Select 不得：

- 在运行时依赖 SourceLens 才能执行视频分析；
- 把 SourceLens 的 Task、Capability、Receipt 表复制成 FrameFlow 业务模型；
- 在 FrameFlow UI 中暴露通用 Agent 编排能力；
- 将 SourceLens 的产品 Backlog 混入 FrameFlow；
- 因 SourceLens 某项技术存在，就强行在 FrameFlow 中使用同一技术；
- 把 FrameFlow 当作 SourceLens 的纯 Demo，忽略独立用户价值。

### 3.3 任务接口

FrameFlow 向 SourceLens 提供的是**开发任务输入**，不是产品 API：

```text
Project facts
Stage
Requirement IDs
Read Set
Write Set
Allowed commands
Network policy
Acceptance criteria
Tests
Evidence contract
Budget
Approval points
```

这些输入由 SourceLens 执行；FrameFlow 仓库只保留任务结果和必要 Evidence，不重复维护 SourceLens 的内部控制面设计。

---

## 4. 研发角色

| 角色 | 职责 | 禁止事项 |
|---|---|---|
| Product Owner | 用户价值、范围、Gate、对外口径 | 把决定完全交给 Agent |
| Product/Domain Designer | 用户流程、规则、状态与边界 | 直接修改 Gold 指标迎合实现 |
| AI Quality Owner | 数据、标注、检测器、评测 | 在 Gold Set 上反复调参 |
| Architecture Steward | 模块、契约、数据、可靠性 | 为未来假设提前拆平台 |
| Implementer | 编码、迁移、测试、局部文档 | 自行批准自己的交付 |
| Verifier | 独立运行测试、检查 Evidence、反例验证 | 修改实现使其通过 |
| Security/Privacy Reviewer | 租户、上传、密钥、Provider、删除 | 只检查“有没有安全文档” |
| User Reviewer | 真实使用和反馈 | 被技术演示代替 |

一个 Agent 可扮演 Implementer；另一个独立 Agent 可扮演 Verifier。最终接受仍由产品所有者完成。

---

## 5. 工作单元

项目只允许四类工作单元。

### 5.1 Experiment

用于验证未知事实：

```text
某检测器是否有效？
某模型能否提供时间码证据？
某阈值是否稳定？
某框架是否兼容？
```

Experiment 必须有：

- 假设；
- 固定输入；
- 度量；
- 预算；
- 停止条件；
- “采用/拒绝/继续研究”结论。

Experiment 不能直接作为生产功能上线。

### 5.2 Feature Slice

形成一个用户可完成的垂直结果，例如：

```text
上传一条视频并看到时间码 Finding
```

而不是：

```text
创建 12 个基础类
```

### 5.3 Hardening

修复已经观察到的可靠性、安全、性能或可维护性问题。

Hardening 必须引用：

- 事故；
- 指标；
- 测试失败；
- 安全发现；
- 用户阻塞。

不得以“企业级需要”为唯一理由。

### 5.4 Documentation / Contract

只允许以下情况独立成任务：

- 产品方向或 Gate 事实源需要收敛；
- OpenAPI / JSON Schema / 数据字典等机器契约；
- ADR；
- 用户/运维 Runbook；
- 评测标注规范。

S0 之后，任何 Stage 不得仅靠 Documentation Task 完成。

---

## 6. 单任务标准循环

```text
1. Select
2. Readiness Check
3. Baseline
4. Implement
5. Self-test
6. Independent Verify
7. Owner Review
8. Merge / Reject
9. Record Learning
```

### 6.1 Select

只从当前 Stage 的 `READY_FOR_DISPATCH` 任务中选择。

禁止：

- 从未来 Stage 偷跑；
- 因某个 Agent 更擅长某技术就改做无关任务；
- 跳过当前阻塞项去创建更多文档。

### 6.2 Readiness Check

派发前检查：

- 上位产品事实是否明确；
- 契约是否存在；
- 依赖 Task 是否已接受；
- 数据和密钥是否可用；
- 验收是否可执行；
- 回滚是否真实可行；
- Agent 是否拥有最小权限。

缺少条件时状态为 `BLOCKED`，不是让 Agent “自行合理补全”。

### 6.3 Baseline

在修改之前保存：

- Commit SHA；
- 当前测试结果；
- 数据库 migration 状态；
- OpenAPI/Schema 哈希；
- 数据集和模型版本；
- 已知失败。

否则无法判断修改造成了什么变化。

### 6.4 Implement

实现阶段只允许修改 Write Set。

发现契约问题时：

```text
停止实现
→ 提交 CONTRACT_CHANGE_REQUEST
→ 由对应 Owner 批准
→ 更新契约
→ 重新派发
```

禁止实现 Agent 直接修改权威契约来适配自己的代码。

### 6.5 Self-test

Implementer 可以执行：

- 局部测试；
- 静态检查；
- 格式检查；
- 契约检查；
- 本地 E2E；
- AI 离线评测子集。

Self-test 只说明“实现者认为可以验证”，不代表 ACCEPTED。

### 6.6 Independent Verify

Verifier 必须从干净环境：

1. 重新 checkout；
2. 按 Evidence 中记录的命令运行；
3. 检查负向路径；
4. 检查 Write Set 越权；
5. 检查测试是否被弱化；
6. 检查数据集污染；
7. 检查回滚；
8. 输出 `PASS / FAIL / INCONCLUSIVE`。

Verifier 不得修改实现分支。

### 6.7 Owner Review

产品所有者回答：

- 用户结果是否出现；
- 是否符合当前 Stage；
- 是否引入新方向；
- 指标是否真实；
- 风险是否可接受；
- 自己是否能解释核心实现。

只有这里可以把任务改为 `ACCEPTED`。

### 6.8 Record Learning

每个任务必须记录：

```text
What changed
What was learned
What remains unknown
What failed
What should not be repeated
```

不得只记录“已完成”。

---

## 7. WIP 限制

### 7.1 阶段级

- 同一时间只有一个 Active Stage；
- 未来 Stage 只能做不产生生产代码的短 Experiment；
- 不允许产品、微服务、Kubernetes 三条主线并行；
- Stage 未通过时，不创建下一阶段完整 Backlog。

### 7.2 任务级

默认最多：

```text
1 个主 Feature Slice
+
1 个独立验证或阻塞 Experiment
```

同时进行。

只有两个任务：

- 写集完全独立；
- 集成点明确；
- 不共享 migration / OpenAPI 区域；
- 不依赖对方未决定的事实；

才允许并行。

### 7.3 文档级

- 同一事实只能有一个权威位置；
- 修改已有权威手册优先于创建新文档；
- 不创建“最终版、最终版2、修订最终版”；
- 临时分析结束后要么合并到权威文档，要么删除/归档；
- 文档 Review 不得无限循环。

---

## 8. Agent 自循环控制

## 8.1 自循环的定义

满足下列任一项即视为自循环风险：

- 连续生成多版计划但没有执行当前验收；
- 同一个问题被多个 Agent 反复审计，没有新证据；
- Agent 为解决任务控制问题继续创建控制文档；
- 实现失败后不断重构无关架构；
- 测试失败时优先修改测试或验收；
- 为满足 Reviewer 继续扩大范围；
- Agent 自己提出新目标、实现、验证并批准；
- 任务连续两次无实质用户结果，只增加文件数量。

## 8.2 尝试上限

同一实现策略最多允许：

```text
初次实现
+
一次基于明确失败原因的修复
```

第二次仍失败时，任务必须进入：

```text
BLOCKED_DIAGNOSIS
```

然后输出：

- 失败现象；
- 复现命令；
- 根因候选；
- 已排除原因；
- 最小下一实验；
- 回退点。

禁止第三次盲目重复。

## 8.3 强制停止条件

出现以下情况立即停止 Agent：

- Write Set 越权；
- 试图读取或提交密钥；
- 删除 `.git` 或重写共享历史；
- 修改旧 Flyway migration；
- 修改 Gold Set 标签；
- 降低验收阈值；
- 删除失败样本；
- 执行未批准网络访问；
- 无限重试；
- 预算超限；
- 试图把 SourceLens 能力复制进 FrameFlow 产品。

## 8.4 停止后的处理

```text
冻结工作区
→ 导出 Diff 和命令轨迹
→ 保留失败证据
→ 独立诊断
→ Owner 决定 Revert / Narrow / Redesign
```

不得让同一个 Agent立即重新规划并继续执行。

---

## 9. 防过度治理

### 9.1 治理价值测试

新增任何治理对象前必须回答：

1. 它阻止过哪个已经发生或高概率事故？
2. 现有机制为什么不够？
3. 是否能自动检查？
4. 维护成本是什么？
5. 删除它会造成什么具体风险？

无法回答时不新增。

### 9.2 六本手册封顶

长期权威“书”固定为六本。

允许新增的只有：

- 机器契约；
- ADR；
- Runbook；
- 数据集/模型卡；
- 临时实验报告；
- 用户帮助文档。

禁止再创建第七本总纲、第八本控制规范或平行 PRD。

### 9.3 ADR 门槛

只有以下问题写 ADR：

- 长期影响且难回退；
- 跨模块或跨语言；
- 有至少两个合理方案；
- 未来维护者需要知道为什么；
- 不记录会再次争论。

普通库升级、类命名、页面布局不写 ADR。

### 9.4 Gate 不能无限增长

新增 Gate 必须替换或合并现有检查，除非有新的高影响风险。

任何 Stage 的顶层 Gate 项建议不超过 15 条；细节应落到自动化测试，而不是继续增加人工清单。

### 9.5 治理债务

以下现象视为治理债务：

- 同一字段存在两套 Schema；
- 文档和代码有不同状态名；
- Task ID 与 Evidence ID 无法映射；
- Agent 输入需要人工拼接多份冲突文档；
- 完成状态只存在自然语言；
- 每次变更都要同步五处以上事实。

治理债务优先通过**删除和合并**解决，不通过新增一层索引解决。

---

## 10. 产品偏航控制

## 10.1 每个 Feature 的五问

进入 Backlog 前必须全部回答“是”：

1. 它是否减少人工观看或提高优质候选保留？
2. 它是否服务于批次质检、证据、复核或 Top-K？
3. 目标用户是否明确表达或数据是否证明需要？
4. 成功是否可以量化？
5. 它是否不属于 SourceLens-AIOS？

任一为“否”，默认拒绝。

## 10.2 常见偏航及处置

| 提议 | 默认处理 |
|---|---|
| 增加文本生成脚本 | 拒绝；属于生成链路，不是当前产品核心 |
| 增加视频编辑时间线 | 拒绝；只保留审片时间轴 |
| 自动发布到平台 | Stage 7 且需要真实请求 |
| 通用内容合规 | 仅保留与当前模板直接相关的风险提示 |
| 通用 Agent Builder | 拒绝并移交 SourceLens |
| 多 Agent 协作 UI | 拒绝；AI 工作流是内部实现 |
| 微服务拆分 | 需要容量或组织证据 |
| 爆款预测 | 拒绝，除非有真实投放标签和可验证模型 |
| 版权最终判断 | 不宣称，只做来源/风险信号 |
| 视频生成 | 只允许作为外部导入连接器，不成为核心能力 |

## 10.3 方向变更

任何 L3 方向变化必须提供：

- 当前方向失败证据；
- 新问题的真实用户证据；
- 与 SourceLens 边界分析；
- 现有资产可复用比例；
- 终止旧 Backlog 的清单；
- 新成功指标；
- 迁移与回滚方案。

不能仅凭“这个方向最近很火”改变项目。

---

## 11. 技术准入控制

## 11.1 技术引入模板

引入新基础设施前必须记录：

```text
Problem
Current measurable limitation
Options
Smallest sufficient option
Operational cost
Failure modes
Removal plan
Decision trigger
```

## 11.2 默认架构预算

MVP 默认只允许：

```text
1 Java Application
1 Web Application
1 Python Worker Image
PostgreSQL
MinIO/S3-compatible Storage
RabbitMQ
```

新增一个长期运行的基础设施组件必须有 ADR 和基准证据。

## 11.3 典型技术门槛

### Redis

只有出现以下问题之一时考虑：

- 跨实例高频临时状态；
- 数据库已被基准证明不适合的限流；
- 可容忍丢失的短期缓存；
- 明确的分布式锁需求且数据库方案不足。

### Kafka

只有出现：

- 大量跨系统不可变事件流；
- 多独立消费者长期重放；
- RabbitMQ + Outbox 已无法满足；
- 真实吞吐与保留需求；

才考虑。

### Kubernetes

只有出现：

- 多环境部署和 Worker 弹性成为真实运维负担；
- 单机/Compose 无法满足试点；
- 有持续部署、故障恢复和资源隔离需求；
- 有人实际维护集群；

才考虑。

### 微服务

只有模块同时具备：

- 独立扩缩容；
- 独立发布；
- 独立数据所有权；
- 独立失败边界；
- 边界稳定；

才允许拆分。

### 向量数据库

先使用离线向量文件或 PostgreSQL 可行方案。只有数据规模、延迟和检索质量基准证明不足后再引入专用向量存储。

## 11.4 禁止“为了学习”污染主线

技术学习实验可以放在：

```text
labs/
```

但不得：

- 成为产品运行依赖；
- 阻塞当前 Stage；
- 宣称为生产能力；
- 迫使主代码适配实验架构。

---

## 12. AI 评测完整性

### 12.1 数据集隔离

数据至少分成：

```text
Development
Calibration
Gold Test
Pilot
```

Gold Test 标签对调优 Agent 不可见。

### 12.2 阈值调整

阈值只能根据 Development / Calibration 调整。

每次阈值变更必须记录：

- 原值；
- 新值；
- 依据数据；
- 预期影响；
- 对 False Reject 的影响；
- 版本。

### 12.3 模型与 Prompt 版本

每个结果必须关联：

```text
Provider
Model ID
Model revision（可获得时）
Prompt version
Sampling/config
Preprocessing version
Detector version
```

Provider 静默升级后，关键 Gate 评测必须重跑。

### 12.4 禁止指标投机

禁止：

- 删除难例；
- 合并类别以提高平均分；
- 只报告最好模型；
- 只报告 Precision，不报告 Recall；
- 忽略未完成 Run；
- 用同一批数据调参和报告最终结果；
- 把 `UNKNOWN` 算作正确；
- 将人工修改后的结果冒充模型原始结果；
- 只展示总分，不提供样本级 Evidence。

### 12.5 发布 Gate

模型、Prompt、检测器或排名变化只有在：

- 回归指标无不可接受下降；
- GCR 和误淘汰护栏通过；
- 成本变化已记录；
- 失败样本可解释；
- 可回退；

后才可发布。

---

## 13. 安全、隐私与数据边界

### 13.1 数据最小化

只保存产品所需数据：

- 原视频；
- 必要衍生物；
- 分析结果；
- 人工反馈；
- 版本与审计信息。

不得默认保存：

- 无关 EXIF；
- 本地路径；
- Provider 返回的完整隐藏元数据；
- 模型内部推理文本；
- 不必要的原始音频副本。

### 13.2 Provider 使用

每个 Provider 必须说明：

- 上传哪些数据；
- 是否用于训练；
- 数据保留；
- 区域；
- 删除机制；
- 失败后的缓存；
- 费用；
- 替代方案。

敏感视频不得在未获批准时发送给外部 Provider。

### 13.3 多租户

所有 Project、Batch、Candidate、Run、Finding、Selection 查询必须包含团队边界。

禁止只依赖前端隐藏资源。

### 13.4 上传

必须处理：

- MIME 与真实媒体类型不一致；
- 超大文件；
- 解码炸弹；
- 非法路径；
- 恶意元数据；
- 重复上传；
- 未完成 multipart；
- 对象存在但数据库记录不存在；
- 数据库记录存在但对象不存在。

### 13.5 密钥

- 不提交 PEM、API Key、对象存储 Secret；
- 本地密钥由脚本生成；
- CI 使用 Secret Store；
- 日志自动脱敏；
- Evidence 不保存明文 Secret；
- Agent 默认无权读取 Secret 路径。

---

## 14. 独立验证标准

Verifier 的结论格式固定为：

```text
Task ID
Baseline SHA
Candidate SHA
Environment
Acceptance-by-acceptance verdict
Negative tests
Scope violations
Evidence completeness
Known limitations
Final verdict: PASS / FAIL / INCONCLUSIVE
```

### PASS

所有阻塞验收通过，未发现不可接受范围越权。

### FAIL

存在可复现的阻塞问题。

### INCONCLUSIVE

环境、Provider、数据或工具问题使结论不足。

`INCONCLUSIVE` 不能被当成 PASS，也不能由 Implementer 自行改写为 PASS。

---

## 15. Evidence 真实性

Evidence 必须满足：

- 可以定位到 Commit；
- 命令可复现；
- 测试结果为工具原始输出或结构化摘要；
- 失败结果同样保存；
- 有数据/模型/Prompt 版本；
- Artifact 有哈希；
- 不依赖 Agent “我已经检查”的陈述；
- 不通过截图替代完整日志；
- 不把旧任务 Evidence 复制到新任务。

Evidence 目录不能成为无限增长的数据垃圾场。长期保留：

- Gate Evidence；
- Release Evidence；
- 关键事故；
- Gold Evaluation；
- 安全验证。

普通中间日志可由 CI Artifact 按保留策略处理。

---

## 16. 事故分类与处理

## 16.1 产品偏航事故

触发：

- 新增方向违反 BOOK-01；
- 旧产品功能重新进入主线；
- SourceLens 能力开始复制。

处理：

```text
冻结新功能
→ 列出偏航 Commit/Task
→ 回退或隔离
→ 修正文档事实源
→ Owner 重新批准
```

## 16.2 误淘汰事故

触发：

- 系统自动淘汰人工认为优质的候选；
- GCR 或 False Reject 护栏跌破阈值。

处理：

```text
立即关闭对应自动 Gate
→ 降级为 REVIEW
→ 保存样本
→ 分析规则/模型/阈值
→ 重跑 Gold Set
→ 重新审批
```

## 16.3 Provider 漂移

触发：

- 同输入结果显著变化；
- 输出 Schema 异常；
- 成本或延迟突增；
- 关键指标下降。

处理：

```text
冻结 Provider Version（若可能）
→ 切换已验证版本
→ 标记受影响 Run
→ 重跑回归集
→ 更新模型卡
```

## 16.4 成本失控

触发：

- 单批次或单候选预算超限；
- 重试造成重复计费；
- 昂贵模型调用比例异常。

处理：

```text
停止后续昂贵 Stage
→ 保留已完成结果
→ 检查幂等和重试
→ 降级预算模式
→ Owner 决定恢复
```

## 16.5 数据泄漏

触发：

- 跨团队访问；
- Secret 泄漏；
- 未授权 Provider 上传；
- 错误公开对象存储链接。

处理：

```text
停止相关服务或入口
→ 撤销密钥/URL
→ 保存审计证据
→ 确认影响范围
→ 通知责任人
→ 修复与回归
→ 事故复盘
```

## 16.6 永久处理中

触发：

- Run 超过业务定义的最大时限仍无终态；
- 无法取消；
- 消息在队列反复循环。

处理：

```text
隔离任务
→ 停止重试
→ 标记明确失败/需要人工处理
→ 检查状态机与幂等
→ 补恢复测试
```

---

## 17. Event-driven Review，而不是会议驱动

个人或小团队项目不强制大量例会。只在以下事件发生时 Review：

- 新 Stage 开始；
- Gate 评测完成；
- L2/L3 变更；
- 安全/隐私事故；
- 指标护栏下降；
- Provider 变更；
- 连续两次任务失败；
- 真实用户反馈与假设冲突；
- 引入新基础设施。

每次 Review 只输出：

```text
Decision
Evidence
Owner
Effective version
Follow-up task（若有）
```

禁止把 Review 变成长篇无结论讨论。

---

## 18. 学习真实性规则

项目目标不仅是交付代码，还要形成属于项目所有者的能力。

### 18.1 必须亲自掌握

项目所有者必须能解释并调试：

- 身份与 RBAC；
- Project / Batch / Candidate / Run 状态机；
- PostgreSQL 事务和关键唯一约束；
- 幂等；
- RabbitMQ ACK、重试与 DLQ；
- MinIO 对象与数据库一致性；
- Python Worker Pipeline；
- 至少一个确定性检测器；
- 至少一个多模态语义断言；
- 排名与 Top-K；
- Gold Set、GCR、False Reject、Top-K Recall；
- 完整 E2E 故障路径。

### 18.2 手写要求

每个主要技术域至少有一个核心切片由项目所有者从空白实现或在不看原实现的情况下重建：

- Java：一个完整业务用例；
- Python：一个检测器及评测；
- Web：一个端到端交互；
- MQ：一个可靠异步链路；
- AI：一个结构化 Provider Adapter；
- Evaluation：一个指标计算和失败分析。

### 18.3 代码理解检查

合并前，项目所有者应能回答：

1. 请求从前端到数据库的调用链是什么？
2. 事务边界在哪里？
3. 重复请求会怎样？
4. Worker 重复回调会怎样？
5. Provider 超时会怎样？
6. Candidate 为什么被淘汰或推荐？
7. 哪个测试证明它？
8. 如何回滚？

回答不了时，任务即使测试通过也不算学习完成。

### 18.4 不以代码量衡量

学习进度使用：

```text
能解释
能复现
能修改
能调试
能写负向测试
能比较方案代价
能在没有 Agent 的情况下实现最小版本
```

不使用：

```text
Commit 数
生成代码行数
Agent 数量
文档页数
技术名词数量
```

---

## 19. Definition of Done

一个 Feature Task 只有同时满足以下条件才是 DONE：

### 产品

- 用户结果与任务目标一致；
- 非目标没有被偷偷加入；
- 权限和失败状态对用户可解释。

### 契约

- OpenAPI / Message Schema / 数据结构已同步；
- 不存在未经批准的破坏性变化；
- 版本可追踪。

### 实现

- 代码只修改允许范围；
- 没有临时 Secret；
- 没有未解释的 TODO；
- 没有通过绕过业务规则实现。

### 测试

- 正向、负向、权限、幂等、失败恢复通过；
- AI 功能通过对应评测；
- 旧功能无未批准回归。

### 观察

- 关键状态、失败、成本可观察；
- 日志不泄漏敏感数据；
- Trace 可以关联批次和 Run。

### 证据

- 每个验收对应 Evidence；
- Commit、版本、环境可追踪；
- 独立 Verifier 给出 PASS；
- 已知限制明确记录。

### 回滚

- 有可执行回滚；
- 数据变化有 forward-fix 或恢复方案；
- Rule、Prompt、Provider 可按版本回退。

### 所有者

- 产品所有者接受；
- 产品所有者能解释核心实现和失败路径。

缺一项时，任务状态只能是 `VERIFYING`、`BLOCKED` 或 `REJECTED`。

---

## 20. Definition of Stage Done

Stage 完成必须满足：

1. 所有阻塞任务 ACCEPTED；
2. Stage Gate 指标通过；
3. 独立验证完成；
4. 风险登记册已更新；
5. 已知限制公开；
6. 下一阶段 DoR 明确；
7. 没有遗留未提交工作树；
8. 没有通过调整目标掩盖失败；
9. 产品所有者签署 Gate Verdict；
10. 对外口径与实际能力一致。

Stage Verdict 只有：

```text
PASS
CONDITIONAL_PASS
FAIL
STOP
PIVOT
```

`CONDITIONAL_PASS` 必须列出截止于下一次派发前的阻塞条件，不得作为长期模糊状态。

---

## 21. 每次继续开发前的最小检查

```text
[ ] 当前 Active Stage 是什么？
[ ] 当前 Gate 还缺什么证据？
[ ] 当前任务是否直接推进该 Gate？
[ ] 当前任务是否属于 FrameFlow，而不是 SourceLens？
[ ] 权威产品事实来自哪一本书？
[ ] Read/Write Set 是否最小？
[ ] 验收是否可执行？
[ ] 是否有独立验证者？
[ ] 失败两次后的停止条件是什么？
[ ] 产品所有者是否能解释这一任务的用户价值？
```

有任一项无法回答时，不开始实现。

---

## 22. 每次任务结束后的最小检查

```text
[ ] 用户可见结果是什么？
[ ] 哪些验收通过，证据在哪里？
[ ] 哪些验收失败？
[ ] 是否越过 Write Set？
[ ] 是否修改了权威契约或阈值？
[ ] 是否新增了不必要技术？
[ ] 是否产生新的产品方向？
[ ] 是否影响 GCR、False Reject 或成本？
[ ] 如何回滚？
[ ] 我能否独立解释和调试？
```

---

## 23. 本手册的自我约束

本手册也不得成为过度治理工具。

以下情况下应简化本手册，而不是新增更多规则：

- 两条规则长期重复；
- 检查可以完全自动化；
- 规则没有阻止任何现实风险；
- 维护规则需要同步多个事实源；
- 规则阻塞了小型可回退 Experiment；
- 规则已经由 SourceLens 自动强制执行。

规则的目标是：

> **让项目以更少事故交付更多真实用户价值。**

不是：

> **让项目看起来治理完善。**
