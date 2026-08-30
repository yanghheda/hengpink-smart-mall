package com.hengpick.mall.decision.report;

import java.util.List;
import java.util.Map;

/** 明确标识缓存来源的降级结果，供后续持久化、Trace 和界面共同消费。 */
public record DemoCacheFallbackReport(
        String generationType,
        String userNotice,
        List<String> traceCodes,
        Map<String, Object> reportSnapshot) {
    public DemoCacheFallbackReport {
        traceCodes = List.copyOf(traceCodes);
        reportSnapshot = Map.copyOf(reportSnapshot);
    }
}
