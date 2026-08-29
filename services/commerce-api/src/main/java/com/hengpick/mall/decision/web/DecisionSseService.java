package com.hengpick.mall.decision.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.event.DecisionStreamStore;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 从指定游标续读事件，并在空闲期间发送心跳。 */
public final class DecisionSseService {
    private static final Duration HEARTBEAT = Duration.ofSeconds(15);
    private final DecisionStreamStore store;
    private final ObjectMapper objectMapper;

    public DecisionSseService(DecisionStreamStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public SseEmitter open(String runId, String lastEventId) {
        var emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        var initialCursor = lastEventId == null || lastEventId.isBlank() ? store.latestId(runId) : lastEventId;
        Thread.startVirtualThread(() -> stream(runId, initialCursor, emitter));
        return emitter;
    }

    private void stream(String runId, String initialCursor, SseEmitter emitter) {
        var cursor = initialCursor;
        var lastSentAt = System.nanoTime();
        try {
            while (true) {
                var events = store.readAfter(runId, cursor, 20);
                for (var event : events) {
                    emitter.send(SseEmitter.event().id(event.eventId()).name(event.eventType())
                            .data(objectMapper.writeValueAsString(event)));
                    cursor = event.eventId();
                    lastSentAt = System.nanoTime();
                    if ("report.completed".equals(event.eventType()) || "run.failed".equals(event.eventType())) {
                        emitter.complete();
                        return;
                    }
                }
                if (System.nanoTime() - lastSentAt >= HEARTBEAT.toNanos()) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    lastSentAt = System.nanoTime();
                }
                Thread.sleep(250);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException | RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }
}
