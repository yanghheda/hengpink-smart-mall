package com.hengpick.mall.decision.report;

import java.util.Map;
import java.util.Objects;

/** 主演示场景的只读报告快照；载荷中的金额和评分已由确定性链路生成。 */
public record DemoReportCacheEntry(
        String scenarioKey,
        ReportVersionSnapshot versions,
        Map<String, Object> reportSnapshot) {
    public DemoReportCacheEntry {
        Objects.requireNonNull(scenarioKey, "场景键不能为空");
        if (scenarioKey.isBlank()) {
            throw new IllegalArgumentException("场景键不能为空");
        }
        Objects.requireNonNull(versions, "版本快照不能为空");
        reportSnapshot = Map.copyOf(Objects.requireNonNull(reportSnapshot, "报告快照不能为空"));
    }
}
