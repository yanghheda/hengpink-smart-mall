package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.DecisionStreamAccessRepository;
import java.util.Objects;

/** 在打开流之前落实 Session 所有权与当前 Run 绑定。 */
public final class DecisionStreamQueryService {
    private final DecisionStreamAccessRepository repository;

    public DecisionStreamQueryService(DecisionStreamAccessRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public String requireCurrentRun(String sessionId, String userId) {
        return repository.findCurrentRunId(sessionId, userId)
                .orElseThrow(() -> new DecisionStreamNotFoundException("决策会话不存在或无权访问"));
    }
}
