package com.hengpick.mall.decision.domain;

/** 仓储在并发写入时发现活跃 Run 唯一约束冲突。 */
public class ActiveRunConstraintViolationException extends RuntimeException {
    public ActiveRunConstraintViolationException(String message) {
        super(message);
    }

    public ActiveRunConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
