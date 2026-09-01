package com.hengpick.mall.decision.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Agent 完成回调的结构化摘要，不承载最终金额或最终评分。 */
public record RunCompletionCallback(
        String runId,
        int runVersion,
        String completionType,
        String contentHash,
        Map<String, Object> resultSummary,
        Instant completedAt) {
    public RunCompletionCallback {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(completionType);
        Objects.requireNonNull(contentHash);
        resultSummary = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(resultSummary)));
        Objects.requireNonNull(completedAt);
        if (runVersion < 1) {
            throw new IllegalArgumentException("Run 版本必须为正数");
        }
    }
}
