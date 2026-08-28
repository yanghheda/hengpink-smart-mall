package com.hengpick.mall.decision.application;

/** 表示同一 Session 已经存在 RUNNING Run。 */
public class ActiveDecisionRunExistsException extends RuntimeException {
    public ActiveDecisionRunExistsException(String message) {
        super(message);
    }

    public ActiveDecisionRunExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
