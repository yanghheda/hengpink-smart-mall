package com.hengpick.mall.decision.application;

/** 对外统一隐藏资源不存在与无权访问的差异。 */
public final class DecisionStreamNotFoundException extends RuntimeException {
    public DecisionStreamNotFoundException(String message) {
        super(message);
    }
}
