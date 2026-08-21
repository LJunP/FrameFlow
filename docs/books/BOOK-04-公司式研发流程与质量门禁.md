# BOOK-04:公司式研发流程与质量门禁

## 1. 流程(每个任务必须走完)
Issue/Task 创建 到 Readiness Check 到 Feature Branch(feature/*,fix/*,release/*,hotfix/*)到 项目所有者实现 到 本地测试 到 Commit 到 Agent Code Review 到 CI 到 独立验证 到 Pull Request 到 Merge 到 Dev 到 Staging 到 Release Approval 到 Production。

即使只有一个人,也必须模拟四个角色:Implementer、Reviewer、Verifier、Release Approver。不要制造大量无价值文档;每个任务只需一个 Task 文件、一个分支、一组测试、一份必要 Evidence。

## 2. 代码管理规则
- 学习主线:frameflow-select/learning-main;所有开发在 Feature Branch 上完成;
- 参考分支 archive/frameflow-select-agent-mvp-v1 与 tag frameflow-select-agent-mvp-v1.0.0 只读,不从中复制完整模块充当学习成果;
- 不 push、不改写历史、不删除 .git;
- Commit 消息遵循 conventional commits(如 feat/fix/docs/chore)。

## 3. 质量门禁
- 同一实现策略最多尝试两次;第二次失败必须输出根因并缩小范围或使用既定 Fallback;
- 不得通过修改测试、阈值、Gold Set、历史迁移来制造 PASS;
- 测试:单元、集成、契约(OpenAPI 差异测试)、越权负例、幂等、恢复;
- 每次 HUMAN_CORE 任务在代码之上还须提交:操作记录、脱敏命令输出、失败记录、问题定位过程、解决方式、个人总结,并回答 Explain-back 问题。

## 4. 环境晋级
local 到 dev 到 staging 到 production,按 LR13/LR14 执行:
- Build once, promote the same image(不分别在 Dev/Staging/Production 重新构建);
- 镜像同时使用 Git SHA、Semantic Version、不可变 Digest;
- Production 发布必须人工审批。

## 5. 证据要求
Evidence 只记录事实与脱敏输出;真实媒体、密钥、密码不进 Git、不进日志、不进前端,不同环境 Secret 独立。

## 6. 防偏航
当前只能有一个 Active Stage;同时最多一个主要开发任务和一个验证任务;不得以不断写文档代替写代码;不得以不断加技术代替解决用户问题;不得因公司也用就强行加入某项基础设施;Redis/RabbitMQ/MinIO 必须有明确业务用途。
