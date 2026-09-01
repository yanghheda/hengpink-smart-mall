package com.hengpick.mall.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    default Optional<DecisionSession> findOwnedSession(String sessionId, String userId) {
        return Optional.empty();
    }

    default List<String> findUserMessages(String sessionId) {
        return List.of();
    }

    void createRunAndAdvanceSession(DecisionRun run, DecisionSession session);

    default void createRunWithMessageAndAdvanceSession(
            DecisionRun run, DecisionSession session, String messageId, String content, Instant createdAt) {
        throw new UnsupportedOperationException("当前仓储不支持追加消息");
    }
}
