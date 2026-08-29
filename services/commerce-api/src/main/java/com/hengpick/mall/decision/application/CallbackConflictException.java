package com.hengpick.mall.decision.application;

/** 同一幂等键携带不同内容时拒绝协议重放。 */
public class CallbackConflictException extends RuntimeException {
    public CallbackConflictException(String message) {
        super(message);
    }
}
