package com.hengpick.mall.engagement.infrastructure;

import java.time.Instant;

record HistoricalReportRow(
        String sessionId,
        String userId,
        int version,
        String selectedSkuId,
        String reportJson,
        String versionsJson,
        Instant createdAt) {}
