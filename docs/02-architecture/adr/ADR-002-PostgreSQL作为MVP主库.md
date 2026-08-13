# ADR-002：PostgreSQL 作为 MVP 唯一事务主库

- 状态：已批准
- 日期：开发前基线

## 决策

FrameFlow MVP 使用 PostgreSQL 作为唯一事务主库。MySQL 作为学习与未来兼容路径，不参与 MVP 双写或双主库设计。

## 原因

- FrameFlow 包含复杂关联、版本快照、审计和 AI 参数等结构化扩展数据；
- PostgreSQL 的事务、约束、JSONB、GIN 索引、窗口函数和 `EXPLAIN ANALYZE` 适合此类场景；
- 双库会额外引入数据归属、迁移、测试、运维和一致性复杂度；
- 初学阶段不应为了覆盖技术栈而制造无业务理由的双库架构。

## 后果

- P0 的本地 Compose、测试和迁移默认使用 PostgreSQL；
- MySQL 的 SQL/索引能力可在独立学习实验中练习；
- 若未来引入异构存储，必须以明确数据所有权、事件和补偿机制设计，禁止隐式双写。
