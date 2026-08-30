package com.hengpick.mall.decision.report;

import java.math.BigDecimal;
import java.util.List;

/** Java 确定性能力生成的权威快照，校验期间不得再读取漂移中的实时数据。 */
public record ReportValidationContext(
        String datasetVersion,
        List<Candidate> candidates,
        List<Price> prices,
        List<Reference> facts,
        List<Reference> evidence) {

    public record Candidate(
            String productId,
            String skuId,
            int rank,
            BigDecimal finalScore,
            boolean hardConstraintsSatisfied,
            boolean simulated,
            String datasetVersion) {}

    public record Price(String pricePlanId, String skuId, BigDecimal finalPrice, String datasetVersion) {}

    public record Reference(
            String referenceId,
            String productId,
            String skuId,
            boolean safe,
            String datasetVersion) {}
}
