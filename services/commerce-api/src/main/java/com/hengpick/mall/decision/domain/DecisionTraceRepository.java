package com.hengpick.mall.decision.domain;

import java.util.Optional;
import java.util.List;

/** 读取单 Run Trace 快照的端口。 */
public interface DecisionTraceRepository {
    Optional<DecisionTraceSnapshot> findByRunId(String runId);
    default List<DecisionTraceListItem> findRecent(int limit) { return List.of(); }
}
