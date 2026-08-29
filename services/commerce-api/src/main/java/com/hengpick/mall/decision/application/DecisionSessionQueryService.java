package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.DecisionSessionSnapshot;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshotRepository;
import java.util.Objects;

/** 为页面刷新和 SSE 降级提供 MySQL 权威快照。 */
public final class DecisionSessionQueryService {
    private final DecisionSessionSnapshotRepository repository;

    public DecisionSessionQueryService(DecisionSessionSnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public DecisionSessionSnapshot requireSnapshot(String sessionId, String userId) {
        return repository.findSnapshot(sessionId, userId)
                .orElseThrow(() -> new DecisionStreamNotFoundException("决策会话不存在或无权访问"));
    }
}
