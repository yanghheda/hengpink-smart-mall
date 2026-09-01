package com.hengpick.mall.decision.domain;

import java.time.Instant;

/** 管理员 Trace 列表所需的脱敏 Run 摘要。 */
public record DecisionTraceListItem(
        String runId, String sessionId, int runVersion, String status,
        String activeNode, String failureCode, Instant startedAt, Instant completedAt) {}
