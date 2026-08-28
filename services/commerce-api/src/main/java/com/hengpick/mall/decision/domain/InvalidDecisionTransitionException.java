package com.hengpick.mall.decision.domain;

/** 表示事件不属于当前状态允许的转换集合。 */
public class InvalidDecisionTransitionException extends RuntimeException {
    public InvalidDecisionTransitionException(String message) {
        super(message);
    }
}
