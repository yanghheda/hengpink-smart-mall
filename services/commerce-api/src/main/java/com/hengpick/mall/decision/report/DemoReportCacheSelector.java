package com.hengpick.mall.decision.report;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 仅允许场景和全部业务版本精确匹配的主演示缓存降级。 */
public final class DemoReportCacheSelector {
    private final List<DemoReportCacheEntry> entries;

    public DemoReportCacheSelector(List<DemoReportCacheEntry> entries) {
        this.entries = List.copyOf(Objects.requireNonNull(entries));
    }

    public Optional<DemoCacheFallbackReport> select(
            String scenarioKey,
            ReportVersionSnapshot requiredVersions) {
        Objects.requireNonNull(scenarioKey, "场景键不能为空");
        Objects.requireNonNull(requiredVersions, "目标版本不能为空");
        return entries.stream()
                .filter(entry -> entry.scenarioKey().equals(scenarioKey))
                .filter(entry -> entry.versions().equals(requiredVersions))
                .findFirst()
                .map(entry -> new DemoCacheFallbackReport(
                        "DEMO_CACHE_FALLBACK",
                        "智能分析服务暂不可用，已返回同版本演示缓存",
                        List.of("DEMO_CACHE_FALLBACK"),
                        entry.reportSnapshot()));
    }
}
