# ADR-004：RabbitMQ 执行任务，Kafka 分发领域事件

- 状态：已批准
- 日期：开发前基线

## 决策

MVP 使用 RabbitMQ 执行待处理的耗时任务；Kafka 在 M11 后用于已发生领域事件的分发与分析。

## 原因

- AI 分析、通知、媒体处理和交付构建需要重试、死信和任务级状态；
- RabbitMQ 更贴合可确认的工作队列；
- Kafka 更适合多消费者、可回放领域事件和后续分析；
- 同时在 MVP 引入两套消息系统会增加初学和运维复杂度。

## 后果

- M6 先用 RabbitMQ + Fake Provider 练可靠异步闭环；
- M11 引入 Outbox、Kafka、消费去重、offset、重试/DLT；
- 两者职责不得混淆。
