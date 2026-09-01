package com.hengpick.mall.recommendation.infrastructure;

import java.time.Instant;

record RecommendationReportRow(
        String sessionId,
        String userId,
        int version,
        String selectedSkuId,
        String reportJson,
        String recommendationSnapshotJson,
        String versionsJson,
        Instant createdAt) {}
