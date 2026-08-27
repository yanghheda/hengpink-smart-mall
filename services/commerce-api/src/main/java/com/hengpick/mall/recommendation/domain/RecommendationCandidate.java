package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RecommendationCandidate(
        ScoreCard rawScoreCard,
        ConfidenceInput confidenceInput,
        List<RiskItem> risks,
        BigDecimal finalPrice,
        List<String> rejectionReasonCodes) {

    public RecommendationCandidate {
        Objects.requireNonNull(rawScoreCard, "原始评分卡不能为空");
        Objects.requireNonNull(confidenceInput, "置信度输入不能为空");
        Objects.requireNonNull(finalPrice, "最终价格不能为空");
        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("最终价格不能为负数");
        risks = List.copyOf(risks);
        rejectionReasonCodes = List.copyOf(rejectionReasonCodes);
    }

    public boolean rejected() {
        return !rejectionReasonCodes.isEmpty();
    }
}
