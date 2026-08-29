package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.decision.event.DecisionEventPublisher;
import com.hengpick.mall.decision.event.DecisionStreamEvent;
import com.hengpick.mall.decision.event.DecisionStreamStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionEventPublisherTest {
    @Test
    void repeatedEventIsIgnoredAndProgressNeverMovesBackward() {
        var store = new InMemoryStreamStore();
        var publisher = new DecisionEventPublisher(store, () -> Instant.parse("2026-08-29T01:00:00Z"));

        assertThat(publisher.publishStage("session-1", "run-1", 1, "PRODUCT", "COMPLETED", 40,
                "商品筛选完成", "step:1")).isTrue();
        assertThat(publisher.publishStage("session-1", "run-1", 1, "PRODUCT", "COMPLETED", 40,
                "商品筛选完成", "step:1")).isFalse();
        assertThat(publisher.publishStage("session-1", "run-1", 1, "INTENT", "COMPLETED", 20,
                "需求理解完成", "step:2")).isFalse();

        assertThat(store.events).hasSize(1);
        assertThat(store.events.getFirst().progress()).isEqualTo(40);
    }

    private static final class InMemoryStreamStore implements DecisionStreamStore {
        private final List<DecisionStreamEvent> events = new ArrayList<>();
        private final List<String> dedupeKeys = new ArrayList<>();
        private int progress;

        @Override
        public boolean append(DecisionStreamEvent event, String dedupeKey) {
            if (dedupeKeys.contains(dedupeKey) || event.progress() < progress) {
                return false;
            }
            dedupeKeys.add(dedupeKey);
            progress = event.progress();
            events.add(event);
            return true;
        }

        @Override
        public String latestId(String runId) {
            return events.isEmpty() ? "0-0" : events.getLast().eventId();
        }

        @Override
        public List<DecisionStreamEvent> readAfter(String runId, String lastEventId, int count) {
            return List.of();
        }
    }
}
