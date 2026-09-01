package com.hengpick.mall.recommendation.application;

import com.hengpick.mall.recommendation.domain.Dimension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReweightResult(
        String sessionId,
        int version,
        String selectedSkuId,
        Map<Dimension, BigDecimal> weights,
        List<RankedCandidate> recommendations,
        String generationType) {
    public record RankedCandidate(int rank, String productId, String skuId, BigDecimal finalScore,
            String pricePlanId, String finalPrice) {}
}
