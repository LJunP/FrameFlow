package com.frameflow.learning.product.web;

/** 分析接口的响应 DTO。 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record FindingResponse(Long id, String dimension, String detector,
                                  String detectorVersion, boolean passed, String severity,
                                  Long timecodeMs, String evidence, String message,
                                  String verdict) {
    }

    /**
     * 审阅页需要同时知道媒体地址和候选终态。
     * ★ 核心：ANALYSIS_ERROR 必须由服务端状态驱动展示，不能因为 Finding 为空
     * 就被前端误写成“未分析或全部通过”；probeError 仅解释入口校验失败。
     */
    public record ContentUrlResponse(String url, String expiresAt,
                                     String status, String probeError) {
    }
}
