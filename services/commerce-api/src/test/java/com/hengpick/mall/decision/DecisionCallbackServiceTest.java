package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hengpick.mall.decision.application.CallbackConflictException;
import com.hengpick.mall.decision.application.DecisionCallbackService;
import com.hengpick.mall.decision.application.RunCompletionOutcome;
import com.hengpick.mall.decision.domain.AgentStepCallback;
import com.hengpick.mall.decision.domain.DecisionCallbackRepository;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionCallbackServiceTest {
    private static final String RUN_ID = "01J5D0M8RZ0000000000000021";
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void repeatedStepWithSameHashIsIdempotentButDifferentContentConflicts() {
        var repository = new InMemoryRepository(true);
        var service = new DecisionCallbackService(repository);
        var step = new AgentStepCallback(RUN_ID, 1, 1, "INTENT", "COMPLETED", NOW, NOW,
                "hash-step-1", Map.of("messageCount", 1), Map.of("intentReady", true));

        service.appendStep(step);
        service.appendStep(step);

        assertThat(repository.steps).hasSize(1);
        var conflicting = new AgentStepCallback(RUN_ID, 1, 1, "INTENT", "COMPLETED", NOW, NOW,
                "hash-step-other", Map.of(), Map.of());
        assertThrows(CallbackConflictException.class, () -> service.appendStep(conflicting));
    }

    @Test
    void staleRunCompletionCannotReplaceCurrentResult() {
        var repository = new InMemoryRepository(false);
        var service = new DecisionCallbackService(repository);
        var completion = new RunCompletionCallback(RUN_ID, 1, "REPORT_READY", "hash-complete-1",
                Map.of("summary", "Stub 报告已就绪"), NOW);

        assertThat(service.complete(completion)).isEqualTo(RunCompletionOutcome.SUPERSEDED);
        assertThat(repository.currentResult).isNull();
        assertThat(repository.superseded).isTrue();
    }

    @Test
    void repeatedCompletionIsIdempotentAndHashConflictIsRejected() {
        var repository = new InMemoryRepository(true);
        var service = new DecisionCallbackService(repository);
        var completion = new RunCompletionCallback(RUN_ID, 2, "REPORT_READY", "hash-complete-2",
                Map.of("summary", "Stub 报告已就绪"), NOW);

        assertThat(service.complete(completion)).isEqualTo(RunCompletionOutcome.APPLIED);
        assertThat(service.complete(completion)).isEqualTo(RunCompletionOutcome.IDEMPOTENT);
        assertThat(repository.applyCount).isEqualTo(1);

        var conflicting = new RunCompletionCallback(RUN_ID, 2, "REPORT_READY", "other-hash",
                Map.of("summary", "内容被篡改"), NOW);
        assertThrows(CallbackConflictException.class, () -> service.complete(conflicting));
    }

    private static final class InMemoryRepository implements DecisionCallbackRepository {
        private final boolean current;
        private final Map<Integer, String> steps = new HashMap<>();
        private String completionHash;
        private Map<String, Object> currentResult;
        private boolean superseded;
        private int applyCount;

        private InMemoryRepository(boolean current) {
            this.current = current;
        }

        @Override
        public Optional<String> findStepContentHash(String runId, int sequence) {
            return Optional.ofNullable(steps.get(sequence));
        }

        @Override
        public void appendStep(AgentStepCallback step) {
            steps.put(step.sequence(), step.contentHash());
        }

        @Override
        public Optional<String> findCompletionContentHash(String runId) {
            return Optional.ofNullable(completionHash);
        }

        @Override
        public boolean completeIfCurrent(RunCompletionCallback completion) {
            completionHash = completion.contentHash();
            if (!current) {
                return false;
            }
            currentResult = completion.resultSummary();
            applyCount++;
            return true;
        }

        @Override
        public void markSuperseded(RunCompletionCallback completion) {
            completionHash = completion.contentHash();
            superseded = true;
        }
    }
}
