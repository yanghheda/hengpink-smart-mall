package com.hengpick.mall.decision.domain;

/** 创建 Run 版本的业务触发来源。 */
public enum RunTriggerType {
    INITIAL_REQUEST,
    MESSAGE_APPENDED,
    USER_RETRY
}
