package com.hengpick.mall.decision.domain;

import java.util.Optional;

/** 为 Step 与完成回调提供幂等和版本隔离的持久化边界。 */
public interface DecisionCallbackRepository {
    default Optional<String> findSessionId(String runId) {
        return Optional.empty();
    }

    Optional<String> findStepContentHash(String runId, int sequence);

    void appendStep(AgentStepCallback step);

    Optional<String> findCompletionContentHash(String runId);

    boolean completeIfCurrent(RunCompletionCallback completion);

    void markSuperseded(RunCompletionCallback completion);
}
