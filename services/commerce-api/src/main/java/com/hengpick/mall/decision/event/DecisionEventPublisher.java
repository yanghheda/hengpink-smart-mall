package com.hengpick.mall.decision.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 统一构造事件信封，并把单调性与幂等性委托给事件存储原子保证。 */
public final class DecisionEventPublisher {
    private final DecisionStreamStore store;
    private final Supplier<Instant> now;

    public DecisionEventPublisher(DecisionStreamStore store, Supplier<Instant> now) {
        this.store = Objects.requireNonNull(store);
        this.now = Objects.requireNonNull(now);
    }

    public boolean publishStarted(String sessionId, String runId, int runVersion) {
        return publish("run.started", sessionId, runId, runVersion, 0,
                Map.of("stage", "LOAD", "status", "RUNNING", "displayText", "决策任务已开始"), "run:started");
    }

    public boolean publishStage(
            String sessionId,
            String runId,
            int runVersion,
            String stage,
            String status,
            int progress,
            String displayText,
            String dedupeKey) {
        return publish("run.stage", sessionId, runId, runVersion, progress,
                Map.of("stage", stage, "status", status, "progress", progress, "displayText", displayText),
                dedupeKey);
    }

    public boolean publishCompleted(String sessionId, String runId, int runVersion, String dedupeKey) {
        return publish("report.completed", sessionId, runId, runVersion, 100,
                Map.of("stage", "COMPLETED", "status", "COMPLETED", "progress", 100,
                        "displayText", "购买分析已完成"), dedupeKey);
    }

    private boolean publish(
            String eventType,
            String sessionId,
            String runId,
            int runVersion,
            int progress,
            Map<String, Object> payload,
            String dedupeKey) {
        var event = new DecisionStreamEvent("", eventType, now.get(), sessionId, runId, runVersion, progress,
                payload);
        return store.append(event, dedupeKey);
    }
}
