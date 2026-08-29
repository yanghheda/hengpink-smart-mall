package com.hengpick.mall.decision.application;

/** 完成回调的稳定处理结果。 */
public enum RunCompletionOutcome {
    APPLIED,
    IDEMPOTENT,
    SUPERSEDED
}
