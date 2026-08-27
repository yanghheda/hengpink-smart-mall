package com.hengpick.mall.recommendation.domain;

import java.util.List;

public record RecommendationResult(
        List<ScoredRecommendation> ranked,
        List<ScoredRecommendation> rejected) {

    public RecommendationResult {
        ranked = List.copyOf(ranked);
        rejected = List.copyOf(rejected);
    }
}
