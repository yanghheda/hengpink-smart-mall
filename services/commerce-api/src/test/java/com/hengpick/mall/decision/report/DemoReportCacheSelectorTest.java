package com.hengpick.mall.decision.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoReportCacheSelectorTest {
    private static final ReportVersionSnapshot VERSIONS =
            new ReportVersionSnapshot("dataset-1", "scoring-1", "pricing-1", "prompt-1");

    @Test
    void exactScenarioAndVersionsReturnExplicitCacheFallback() {
        var cached = new DemoReportCacheEntry(
                "parents-phone",
                VERSIONS,
                Map.of("summary", "基础分析", "recommendations", List.of(Map.of("skuId", "SKU-1"))));

        var result = new DemoReportCacheSelector(List.of(cached)).select("parents-phone", VERSIONS);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().generationType()).isEqualTo("DEMO_CACHE_FALLBACK");
        assertThat(result.orElseThrow().traceCodes()).containsExactly("DEMO_CACHE_FALLBACK");
        assertThat(result.orElseThrow().userNotice()).isEqualTo("智能分析服务暂不可用，已返回同版本演示缓存");
    }

    @Test
    void anyVersionMismatchRefusesCacheInsteadOfUsingNearestEntry() {
        var cached = new DemoReportCacheEntry("parents-phone", VERSIONS, Map.of("summary", "旧报告"));
        var selector = new DemoReportCacheSelector(List.of(cached));

        assertThat(selector.select(
                "parents-phone",
                new ReportVersionSnapshot("dataset-2", "scoring-1", "pricing-1", "prompt-1")))
                .isEmpty();
        assertThat(selector.select(
                "parents-phone",
                new ReportVersionSnapshot("dataset-1", "scoring-1", "pricing-1", "prompt-2")))
                .isEmpty();
    }

    @Test
    void differentScenarioDoesNotReuseCache() {
        var cached = new DemoReportCacheEntry("parents-phone", VERSIONS, Map.of("summary", "旧报告"));

        assertThat(new DemoReportCacheSelector(List.of(cached)).select("office-monitor", VERSIONS))
                .isEmpty();
    }
}
