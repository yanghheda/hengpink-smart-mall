package com.hengpick.mall.decision.domain;

import java.util.Optional;

/** 按所有者读取持久化会话快照，不依赖 Redis 事件是否存在。 */
public interface DecisionSessionSnapshotRepository {
    Optional<DecisionSessionSnapshot> findSnapshot(String sessionId, String userId);
}
