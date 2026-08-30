package com.hengpick.mall.decision.domain;

import java.util.Optional;

/** 读取单 Run Trace 快照的端口。 */
public interface DecisionTraceRepository {
    Optional<DecisionTraceSnapshot> findByRunId(String runId);
}
