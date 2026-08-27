package com.hengpick.mall.recommendation.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ScoreCard(
        /* 被评分的可购买 SKU。 */
        String skuId,
        /* 手机能力计算结果及其公式来源。 */
        List<CapabilityScore> capabilityScores,
        /* 尚未计算最终推荐指数的五维原始分。 */
        Map<Dimension, DimensionScore> dimensionScores,
        /* 本次五维计算使用的评分配置版本。 */
        String scoringVersion) {

    public ScoreCard {
        capabilityScores = List.copyOf(capabilityScores);
        dimensionScores = Map.copyOf(new EnumMap<>(dimensionScores));
    }
}
