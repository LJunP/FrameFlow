package com.frameflow.learning.product.mq;

/** 派发给 worker 的任务消息（JSON，Python 端按同名字段解析）。 */
public record AnalysisTaskMessage(
        long runId,
        long candidateId,
        long batchId,
        String objectKey,
        String contentType,
        long sizeBytes,
        /** 质检标准（profile 版本的 spec JSON 原文）——消息自包含，worker 不回查。 */
        String profileSpec,
        /** Brief 快照原文（F6 语义对齐需要；同样随消息走，保持自包含）。 */
        String briefContent,
        /** 已投递尝试次数（重放/重投时递增），worker 据此判断是否为毒消息。 */
        int deliveryAttempt) {
}
