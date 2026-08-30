package com.hengpick.mall.decision.report;

import java.math.BigDecimal;
import java.util.List;

/** Python 完成文案投影后、尚未进入持久化边界的最终报告草稿。 */
public record FinalReportDraft(
        String datasetVersion,
        String summary,
        List<Recommendation> recommendations,
        List<String> overallDataGaps) {

    public record Recommendation(
            int rank,
            String productId,
            String skuId,
            BigDecimal finalScore,
            String pricePlanId,
            BigDecimal finalPrice,
            boolean simulated,
            List<Reason> reasons) {}

    public record Reason(String text, List<String> factIds, List<String> evidenceIds) {}
}
