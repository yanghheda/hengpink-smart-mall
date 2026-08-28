package com.hengpick.mall.decision.domain;

/** 为 Run 创建用例提供原子持久化边界。 */
public interface DecisionRunRepository {
    boolean hasActiveRun(String sessionId);

    void createRunAndAdvanceSession(DecisionRun run, DecisionSession session);
}
