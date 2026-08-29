package com.hengpick.mall.decision.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Agent 节点回调的脱敏持久化数据。 */
public record AgentStepCallback(
        String runId,
        int runVersion,
        int sequence,
        String node,
        String status,
        Instant startedAt,
        Instant completedAt,
        String contentHash,
        Map<String, Object> inputSummary,
        Map<String, Object> outputSummary) {
    public AgentStepCallback {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(node);
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(completedAt);
        Objects.requireNonNull(contentHash);
        inputSummary = Map.copyOf(Objects.requireNonNull(inputSummary));
        outputSummary = Map.copyOf(Objects.requireNonNull(outputSummary));
        if (runVersion < 1 || sequence < 1) {
            throw new IllegalArgumentException("Run 版本和 Step 序号必须为正数");
        }
    }
}
