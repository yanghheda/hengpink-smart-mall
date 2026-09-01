package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.decision.domain.RunCompletionCallback;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunCompletionCallbackTest {
    @Test
    void optionalNullSummaryValueDoesNotCauseRawNullPointerException() {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("generationType", "TOOL_BACKED_REVIEW_STUB");
        summary.put("reportNarrative", null);

        var callback = new RunCompletionCallback(
                "RUN-1", 1, "CLARIFICATION_REQUIRED", "hash", summary, Instant.EPOCH);

        assertThat(callback.resultSummary()).containsEntry("reportNarrative", null);
        assertThatThrownBy(() -> callback.resultSummary().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void summaryMapIsDefensivelyCopied() {
        var summary = new LinkedHashMap<String, Object>(Map.of("status", "ready"));
        var callback = new RunCompletionCallback(
                "RUN-1", 1, "REPORT_READY", "hash", summary, Instant.EPOCH);

        summary.put("status", "changed");

        assertThat(callback.resultSummary()).containsEntry("status", "ready");
    }
}
