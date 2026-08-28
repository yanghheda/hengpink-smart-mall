package com.hengpick.mall.decision.domain;

/** 驱动决策状态变化的受控领域事件。 */
public enum DecisionEvent {
    RUN_REQUESTED,
    CLARIFICATION_REQUIRED,
    MESSAGE_APPENDED,
    REPORT_VALIDATED,
    DEGRADED_RESULT,
    UNRECOVERABLE_ERROR,
    NEWER_RUN_CREATED,
    USER_CANCELLED
}
