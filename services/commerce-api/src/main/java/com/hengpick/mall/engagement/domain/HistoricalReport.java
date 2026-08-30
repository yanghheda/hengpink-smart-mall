package com.hengpick.mall.engagement.domain;

import java.time.Instant;
import java.util.Map;

public record HistoricalReport(
        String sessionId,
        String userId,
        int version,
        String selectedSkuId,
        Map<String, Object> report,
        Map<String, Object> versions,
        Instant createdAt) {}
