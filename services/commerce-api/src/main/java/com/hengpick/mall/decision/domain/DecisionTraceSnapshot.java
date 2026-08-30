package com.hengpick.mall.decision.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 单次决策运行的只读脱敏 Trace 快照。 */
public record DecisionTraceSnapshot(
        String runId,
        String sessionId,
        @JsonIgnore String ownerId,
        int runVersion,
        String status,
        String activeNode,
        String failureCode,
        List<String> degradationCodes,
        String traceId,
        Instant startedAt,
        Instant completedAt,
        String modelVersion,
        String promptVersion,
        String datasetVersion,
        String scoringVersion,
        String pricingRuleVersion,
        String embeddingVersion,
        Integer tokenInput,
        Integer tokenOutput,
        BigDecimal estimatedCost,
        List<Step> steps) {
    /** 单个 Agent 节点的公开执行摘要。 */
    public record Step(
            int sequence,
            String node,
            String status,
            Instant startedAt,
            Instant completedAt,
            long durationMs,
            String errorCode,
            List<String> warningCodes,
            Map<String, Object> inputSummary,
            Map<String, Object> outputSummary) {}
}
