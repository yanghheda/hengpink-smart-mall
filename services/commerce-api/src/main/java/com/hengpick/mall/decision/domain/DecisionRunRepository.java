package com.hengpick.mall.decision.domain;

/** 为 Run 创建用例提供原子持久化边界。 */
public interface DecisionRunRepository {
    default void createInitialRun(
            DecisionSession session,
            DecisionRun run,
            String title,
            String intentJson,
            String weightsJson,
            String datasetVersion,
            String categorySchemaVersion,
            String messageId,
            String messageContent) {
        throw new UnsupportedOperationException("当前仓储不支持创建首次决策 Run");
    }

    boolean hasActiveRun(String sessionId);

    void createRunAndAdvanceSession(DecisionRun run, DecisionSession session);
}
