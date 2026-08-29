package com.hengpick.mall.decision.domain;

import java.util.Optional;

/** 查询用户当前有权订阅的 Run，不暴露持久化细节。 */
public interface DecisionStreamAccessRepository {
    Optional<String> findCurrentRunId(String sessionId, String userId);
}
