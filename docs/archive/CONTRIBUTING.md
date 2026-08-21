# Contributing to FrameFlow Select

1. 只处理 `.frameflow/master-plan.yaml` 当前 Stage 的一个 Task。
2. 开始前创建 `.frameflow/tasks/<TASK-ID>.json`。
3. 先契约、再实现；实现不得擅自修改验收来适配代码。
4. 每个 Task 必须有测试、Evidence、独立 worktree 验证和可回滚 Commit。
5. 不允许 push、发布或使用真实媒体作为默认测试数据。
6. 不创建新的长期总纲；更新 `docs/core` 中对应手册。
7. 不引入微服务、Kafka、Kubernetes、Istio 或 Agent Infra。
8. 失败两次后缩小范围，不无限重试。
