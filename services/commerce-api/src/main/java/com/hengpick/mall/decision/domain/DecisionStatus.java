package com.hengpick.mall.decision.domain;

/** 决策会话对客户端展示的当前状态。 */
public enum DecisionStatus {
    DRAFT,
    RUNNING,
    WAITING_CLARIFICATION,
    COMPLETED,
    PARTIAL,
    FAILED,
    SUPERSEDED,
    CANCELLED
}
