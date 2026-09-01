package com.hengpick.mall.recommendation.domain;

import java.util.Optional;

public interface RecommendationReportRepository {
    Optional<StoredRecommendationReport> findCurrent(String userId, String sessionId);

    void publishInitial(StoredRecommendationReport report);

    boolean appendReweighted(StoredRecommendationReport report, int expectedVersion);
}
