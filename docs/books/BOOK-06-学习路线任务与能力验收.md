# BOOK-06:学习路线任务与能力验收

## 1. 阶段顺序(不得跳过;AGENT_SUPPORT 阶段由 Agent 完成,其余以 HUMAN_CORE 为主)
- LR0 保留旧成果并建立学习主线(AGENT_SUPPORT),Gate:LEARNING_REBUILD_READY;
- LR1 Git/Java/最小垂直切片基础(HUMAN_CORE),Gate:FOUNDATION_SKILLS_ACCEPTED;
- LR2 Linux 服务器与 SSH(HUMAN_CORE),Gate:NONPROD_LINUX_READY;
- LR3 Docker 与基础服务手工部署(HUMAN_CORE),Gate:NONPROD_INFRA_READY;
- LR4 环境隔离与第一个服务器版本(HUMAN_CORE+PAIR),Gate:FIRST_SERVER_RELEASE_ACCEPTED;
- LR5 身份、团队与权限(HUMAN_CORE),Gate:IDENTITY_SLICE_ACCEPTED;
- LR6 Project、Quality Profile 与数据库设计(HUMAN_CORE),Gate:DOMAIN_FOUNDATION_ACCEPTED;
- LR7 Redis 工程实践(HUMAN_CORE),Gate:REDIS_ENGINEERING_ACCEPTED;
- LR8 Batch、Candidate 与 MinIO(HUMAN_CORE+PAIR),Gate:MEDIA_INGEST_ACCEPTED;
- LR9 RabbitMQ、异步任务和可靠性(HUMAN_CORE),Gate:ASYNC_ENGINE_ACCEPTED;
- LR10 Python 视频处理(HUMAN_CORE),Gate:DETERMINISTIC_VIDEO_QA_ACCEPTED;
- LR11 多模态 AI 与 Evaluation(HUMAN_CORE+PAIR),Gate:AI_QA_ACCEPTED;
- LR12 Web 产品与完整联调(HUMAN_CORE+PAIR),Gate:FULL_STACK_PRODUCT_ACCEPTED;
- LR13 CI/CD 和环境晋级(HUMAN_CORE),Gate:DELIVERY_PIPELINE_ACCEPTED;
- LR14 Production 上线(HUMAN_CORE),Gate:PRODUCTION_RELEASE_ACCEPTED;
- LR15 监控、日志、备份和事故演练(HUMAN_CORE),Gate:OPERATIONS_SKILLS_ACCEPTED;
- LR16 真实用户和真实视频验证(HUMAN_CORE+PAIR),Gate:PILOT_VALUE_PROVEN / PILOT_NOT_PROVEN;
- LR17 最终个人能力验收(仅项目所有者批准),Gate:LEARNING_PROJECT_COMPLETE。

## 2. 任务所有权
- HUMAN_CORE:项目所有者亲自完成核心代码/部署/配置/操作;Agent 只提供任务说明、检查清单、分层提示(第一层目标与验收,第二层文件级提示)、审查、故障分析、验证脚本;不得代做或替所有者提交最终结果;
- PAIR:所有者先完成核心部分后,Agent 可协助异常处理、测试、重构、SQL、性能、前端体验、Docker 镜像、CI/CD;
- AGENT_SUPPORT:迁移、归档、目录、测试基础设施、非核心样板、代码审查、日志分析、文档校验、安全扫描、独立验证。
- 即使 Pair 完成,也必须要求项目所有者解释调用链与失败路径。

## 3. 能力矩阵与验收
- 每个 HUMAN_CORE Task 的三类产物:代码与测试、操作与 Evidence(脱敏命令输出、失败记录、定位过程、解决方式、总结)、Explain-back 回答;
- 没有 Explain-back,不得把能力标记为掌握;
- 代码通过不等于学习完成;
- Agent 不得自行宣布项目所有者掌握某项能力,不得自行设置 LEARNING_PROJECT_COMPLETE。

## 4. 每阶段准入与退出
- 准入:上一阶段 Gate Accepted + 本阶段 Readiness Check 通过 + 环境可用;
- 退出:全部验收标准 + 测试 + Evidence + Explain-back 通过,Gate 记录于 .learning/state.yaml。

## 5. 个人能力验收清单(仅项目所有者批准 LR17)
从空白部署、解释每个组件、完成一次新功能、定位一次线上故障、执行一次回滚、恢复一次数据库、解释 Redis 与数据库一致性、解释 RabbitMQ 可靠性、解释 Docker 网络与 Volume、解释 Nginx 与 HTTPS、解释 AI Evaluation、解释产品关键指标。
