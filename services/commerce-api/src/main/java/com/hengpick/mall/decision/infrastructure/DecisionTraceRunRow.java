package com.hengpick.mall.decision.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;

/** 数据库中的 Run Trace 头部查询行。 */
public record DecisionTraceRunRow(String runId, String sessionId, String ownerId, int runVersion, String status,
        String activeNode, String failureCode, String degradationCodesJson, String traceId, Instant startedAt,
        Instant completedAt, String modelVersion, String promptVersion, String datasetVersion, String scoringVersion,
        String pricingRuleVersion, String embeddingVersion, Integer tokenInput, Integer tokenOutput,
        BigDecimal estimatedCost) {}
