package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.decision.application.DecisionTraceService;
import com.hengpick.mall.decision.domain.DecisionTraceRepository;
import com.hengpick.mall.decision.domain.DecisionTraceSnapshot;
import com.hengpick.mall.identity.application.ObjectAccessDeniedException;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.identity.domain.RequestSubject;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionTraceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private final DecisionTraceService service = new DecisionTraceService(new StubRepository(),
            new ObjectAccessGuard(record -> {}, value -> value,
                    Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void demoUserCannotReadTrace() {
        assertThatThrownBy(() -> service.getTrace(new RequestSubject("USER-1", "DEMO_USER"), "RUN-1"))
                .isInstanceOf(ObjectAccessDeniedException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void adminCanReadAnotherUsersSanitizedTrace() {
        var trace = service.getTrace(new RequestSubject("ADMIN-1", "DEMO_ADMIN"), "RUN-1");

        assertThat(trace.runId()).isEqualTo("RUN-1");
        assertThat(trace.ownerId()).isEqualTo("USER-1");
    }

    @Test
    void traceKeepsFailureAndAllowlistedSummariesWithoutPrivateReasoning() {
        var trace = service.getTrace(new RequestSubject("USER-1", "DEMO_ADMIN"), "RUN-1");

        assertThat(trace.failureCode()).isEqualTo("TOOL_TIMEOUT");
        assertThat(trace.degradationCodes()).containsExactly("QDRANT_UNAVAILABLE");
        assertThat(trace.steps().getFirst().errorCode()).isEqualTo("TOOL_TIMEOUT");
        assertThat(trace.steps().getFirst().inputSummary()).containsOnlyKeys("categoryId", "candidateCount");
        assertThat(trace.steps().getFirst().outputSummary()).containsOnlyKeys("resultCount", "evidenceIds");
        assertThat(trace.steps().getFirst().inputSummary().toString()).doesNotContain("用户原文", "chainOfThought");
    }

    private static final class StubRepository implements DecisionTraceRepository {
        @Override
        public Optional<DecisionTraceSnapshot> findByRunId(String runId) {
            return Optional.of(new DecisionTraceSnapshot("RUN-1", "SESSION-1", "USER-1", 3, "FAILED",
                    "REVIEW", "TOOL_TIMEOUT", List.of("QDRANT_UNAVAILABLE"), "trace-1", NOW.minusSeconds(2), NOW,
                    "model-demo", "prompt-v3", "dataset-v1", "score-v2", "price-v4", "embed-v2", 1200, 220,
                    new BigDecimal("0.0180"), List.of(new DecisionTraceSnapshot.Step(2, "REVIEW", "FAILED",
                            NOW.minusSeconds(1), NOW, 1000L, "TOOL_TIMEOUT", List.of("RETRIEVAL_DEGRADED"),
                            Map.of("categoryId", "PHONE", "candidateCount", 6, "userText", "用户原文",
                                    "chainOfThought", "私有推理"),
                            Map.of("resultCount", 0, "evidenceIds", List.of("EV-1"), "rawResponse", "模型原文")))));
        }
    }
}
