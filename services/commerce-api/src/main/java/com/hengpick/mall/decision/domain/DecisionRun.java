package com.hengpick.mall.decision.domain;

import java.time.Instant;

/** 一次版本化决策尝试；终态记录只读，重试应创建新对象。 */
public record DecisionRun(
        String id,
        String sessionId,
        int runVersion,
        RunStatus status,
        RunTriggerType triggerType,
        Instant startedAt,
        Instant completedAt) {}
