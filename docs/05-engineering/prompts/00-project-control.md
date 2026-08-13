# Prompt 00：项目总控

请在 FrameFlow 工作区执行受控研发任务。

## 开始前

1. 读取 `README.md`、`docs/00-governance/documentation-policy.md`、`project-status.md` 和当前阶段权威文档。
2. 检查源码、构建、迁移、测试、Compose、部署和证据；不存在的内容标为 UNKNOWN。
3. 输出 FACT/DECISION/PLAN/UNKNOWN 表、scope、non-goals、文件清单、测试计划和风险。

## 约束

- 不访问 `jcm_media_api`，不读取真实凭据、客户数据、素材或完整外部 payload。
- 不创建权威文档未批准的模块、服务、数据库或消息系统。
- 不做未经确认的删除、覆盖、迁移、发布或外部发送。
- 模块边界、服务边界、数据所有权和安全策略必须以 ADR 为准。

## 结束时

输出实际改动、实际命令和结果、测试状态、证据路径、未完成项、限制、回滚方式和下一阶段门禁；同步更新 `project-status.md` 与 `evidence-index.md`。
