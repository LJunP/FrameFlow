# ADR-007：AI Provider 适配与人工确认

- 状态：Accepted
- 日期：2026-08-11

## 决策

- 通过 `AiProviderPort` 和 Adapter 隔离 Fake Provider 与真实 Provider。
- Provider 输出必须经过结构化校验、超时、有限重试、脱敏和成本记录。
- AI 结果只保存为 `Suggestion`，人工确认后才写入正式业务数据。
- Provider、模型、Prompt 模板和外部请求标识必须可追溯。

## 后果

- 可以先用 Fake Provider 完成可重复测试；
- 需要维护输出 schema、Provider 适配和失败任务状态；
- 不允许 AI 直接修改权限、交付锁定或核心状态机。
