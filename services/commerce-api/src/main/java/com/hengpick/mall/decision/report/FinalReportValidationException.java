package com.hengpick.mall.decision.report;

/** 携带稳定阶段和原因码的报告拒绝结果。 */
public final class FinalReportValidationException extends RuntimeException {
    private final ReportValidationStage stage;
    private final String code;

    public FinalReportValidationException(ReportValidationStage stage, String code, String message) {
        super(message);
        this.stage = stage;
        this.code = code;
    }

    public ReportValidationStage stage() {
        return stage;
    }

    public String code() {
        return code;
    }
}
