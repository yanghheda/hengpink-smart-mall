package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
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
        String scoringVersion,
        /* 归一化后的顶层权重；原始维度分阶段为空。 */
        Map<Dimension, BigDecimal> normalizedWeights,
        /* 尚未叠加置信度和风险的基础分。 */
        BigDecimal baseScore,
        /* 与推荐指数分离保存的置信度明细。 */
        ConfidenceScore confidence,
        /* 由置信度换算出的最终分乘数。 */
        BigDecimal confidenceCoefficient,
        /* 去重后实际生效的风险项。 */
        List<RiskItem> appliedRisks,
        /* 封顶后的风险总扣分。 */
        BigDecimal riskPenalty,
        /* 用未格式化数值参与排序的最终推荐指数。 */
        BigDecimal finalScore) {

    public ScoreCard {
        capabilityScores = List.copyOf(capabilityScores);
        dimensionScores = Map.copyOf(new EnumMap<>(dimensionScores));
        normalizedWeights = normalizedWeights == null || normalizedWeights.isEmpty()
                ? Map.of()
                : Map.copyOf(new EnumMap<>(normalizedWeights));
        appliedRisks = appliedRisks == null ? List.of() : List.copyOf(appliedRisks);
    }

    public ScoreCard(
            String skuId,
            List<CapabilityScore> capabilityScores,
            Map<Dimension, DimensionScore> dimensionScores,
            String scoringVersion) {
        this(skuId, capabilityScores, dimensionScores, scoringVersion,
                Map.of(), null, null, null, List.of(), null, null);
    }
}
