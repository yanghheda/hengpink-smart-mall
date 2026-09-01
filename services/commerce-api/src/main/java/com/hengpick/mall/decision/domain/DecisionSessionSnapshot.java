package com.hengpick.mall.decision.domain;

/** 从 MySQL 恢复页面所需的最小决策会话事实。 */
public record DecisionSessionSnapshot(
        String sessionId,
        String currentRunId,
        int currentRunVersion,
        String status,
        Integer currentReportVersion,
        String clarificationJson) {
    public DecisionSessionSnapshot(
            String sessionId, String currentRunId, int currentRunVersion,
            String status, Integer currentReportVersion) {
        this(sessionId, currentRunId, currentRunVersion, status, currentReportVersion, null);
    }
}
