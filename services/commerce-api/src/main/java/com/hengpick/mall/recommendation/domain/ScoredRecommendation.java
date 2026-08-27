package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.List;

public record ScoredRecommendation(
        RecommendationStatus status,
        ScoreCard scoreCard,
        BigDecimal finalPrice,
        List<String> rejectionReasonCodes) {

    public ScoredRecommendation {
        rejectionReasonCodes = List.copyOf(rejectionReasonCodes);
    }
}
