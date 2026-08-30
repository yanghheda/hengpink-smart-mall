package com.hengpick.mall.decision.application;

/** 请求的决策 Run 不存在。 */
public class DecisionTraceNotFoundException extends RuntimeException {
    public DecisionTraceNotFoundException() {
        super("Trace 不存在");
    }
}
