package com.hengpick.mall.recommendation.domain;

import java.util.List;

@FunctionalInterface
public interface CandidateRecalculator {

    List<RecommendationCandidate> recalculate(AdjustmentContext context);
}
