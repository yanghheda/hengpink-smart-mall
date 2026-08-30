package com.hengpick.mall.decision.report;

/** 最终事实校验的固定短路顺序。 */
public enum ReportValidationStage {
    SCHEMA,
    ID,
    HARD_CONSTRAINT,
    RANKING,
    AMOUNT,
    EVIDENCE,
    VERSION
}
