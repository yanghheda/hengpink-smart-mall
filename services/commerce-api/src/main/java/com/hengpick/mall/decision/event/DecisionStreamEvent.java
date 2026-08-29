package com.hengpick.mall.decision.event;

import java.time.Instant;
import java.util.Map;

/** 面向用户的决策阶段事件，不包含模型隐式推理。 */
public record DecisionStreamEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sessionId,
        String runId,
        int runVersion,
        int progress,
        Map<String, Object> payload) {
    public DecisionStreamEvent withEventId(String id) {
        return new DecisionStreamEvent(id, eventType, occurredAt, sessionId, runId, runVersion, progress, payload);
    }
}
