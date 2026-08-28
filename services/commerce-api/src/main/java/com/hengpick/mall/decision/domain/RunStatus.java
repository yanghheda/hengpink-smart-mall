package com.hengpick.mall.decision.domain;

/** 单个不可覆写 Run 版本的执行状态。 */
public enum RunStatus {
    RUNNING,
    WAITING_CLARIFICATION,
    COMPLETED,
    PARTIAL,
    FAILED,
    SUPERSEDED,
    CANCELLED
}
