package com.hengpick.mall.decision.domain;

/** 决策任务容器；Run 版本递增，历史 Run 不在此对象中覆写。 */
public record DecisionSession(
        String id,
        String userId,
        DecisionStatus status,
        int currentRunVersion,
        int currentReportVersion,
        long version) {

    public DecisionSession advanceToRunningRun(int nextRunVersion) {
        if (nextRunVersion != currentRunVersion + 1) {
            throw new IllegalArgumentException("新 Run 版本必须严格递增 1");
        }
        return new DecisionSession(id, userId, DecisionStatus.RUNNING, nextRunVersion, currentReportVersion,
                version + 1);
    }
}
