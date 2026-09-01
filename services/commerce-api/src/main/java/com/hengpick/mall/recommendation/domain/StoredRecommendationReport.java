package com.hengpick.mall.recommendation.domain;

import java.time.Instant;
import java.util.Map;

/** 数据库中的报告正文、版本矩阵和权威重算快照。 */
public record StoredRecommendationReport(
        String sessionId,
        String userId,
        int version,
        String selectedSkuId,
        Map<String, Object> report,
        Map<String, Object> versions,
        RecommendationReportSnapshot snapshot,
        Instant createdAt) {
    public StoredRecommendationReport {
        report = Map.copyOf(report);
        versions = Map.copyOf(versions);
    }
}
