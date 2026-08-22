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
}
