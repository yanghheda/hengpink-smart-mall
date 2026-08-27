package com.hengpick.mall.recommendation.domain;

import java.util.List;
import java.util.Objects;

public record RecommendationSnapshot(
        long reportVersion,
        AdjustmentContext context,
        List<RecommendationCandidate> candidates,
        RecommendationResult result) {

    public RecommendationSnapshot {
        if (reportVersion < 1) throw new IllegalArgumentException("报告版本必须从 1 开始");
        Objects.requireNonNull(context, "调整上下文不能为空");
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(result, "推荐结果不能为空");
    }
}
