package com.hengpick.mall.decision.infrastructure;

import java.time.Instant;

/** 数据库中的 Agent Step Trace 查询行。 */
public record DecisionTraceStepRow(int sequence, String node, String status, Instant startedAt, Instant completedAt,
        long durationMs, String errorCode, String warningCodesJson, String inputSummaryJson,
        String outputSummaryJson) {}
