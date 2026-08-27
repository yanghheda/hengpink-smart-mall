package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DimensionScoreCalculator {

    private static final Map<Dimension, Map<String, BigDecimal>> FIXED_FORMULAS = Map.of(
            Dimension.PRICE_VALUE, weights(
                    "pricePercentile", "0.50", "configurationValue", "0.30", "budgetFit", "0.20"),
            Dimension.REVIEW_QUALITY, weights(
                    "topicSentiment", "0.45", "trustedSampleSize", "0.20",
                    "evidenceConsistency", "0.20", "scenarioRelevance", "0.15"),
            Dimension.PROMOTION_VALUE, weights(
                    "savingRatio", "0.60", "availability", "0.20", "simplicity", "0.20"),
            Dimension.RELIABILITY, weights(
                    "shopService", "0.30", "afterSalesCoverage", "0.30",
                    "warranty", "0.20", "dataCompleteness", "0.20"));

    public ScoreCard calculate(
            String skuId,
            String scoringVersion,
            List<CapabilityScore> capabilityScores,
            Map<Dimension, List<WeightedMetric>> inputs) {
        if (skuId == null || skuId.isBlank()) throw new IllegalArgumentException("SKU 标识不能为空");
        if (scoringVersion == null || scoringVersion.isBlank()) throw new IllegalArgumentException("评分版本不能为空");
        if (!inputs.keySet().equals(java.util.EnumSet.allOf(Dimension.class))) {
            throw new IllegalArgumentException("必须提供且只能提供五个顶层维度");
        }

        var scores = new EnumMap<Dimension, DimensionScore>(Dimension.class);
        for (var dimension : Dimension.values()) scores.put(dimension, calculateDimension(dimension, inputs.get(dimension)));
        return new ScoreCard(skuId, capabilityScores, scores, scoringVersion);
    }

    private DimensionScore calculateDimension(Dimension dimension, List<WeightedMetric> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("维度指标不能为空");
        validateFixedFormula(dimension, inputs);
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal knownWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        var factIds = new java.util.ArrayList<String>();
        var missingKeys = new java.util.ArrayList<String>();
        var keys = new java.util.HashSet<String>();
        for (var input : inputs) {
            if (!keys.add(input.metric().key())) throw new IllegalArgumentException("维度指标键不能重复");
            totalWeight = totalWeight.add(input.weight());
            if (!input.metric().known()) {
                missingKeys.add(input.metric().key());
                continue;
            }
            knownWeight = knownWeight.add(input.weight());
            weightedScore = weightedScore.add(input.metric().normalizedScore().multiply(input.weight()));
            factIds.add(input.metric().factId());
        }
        if (totalWeight.signum() == 0) throw new IllegalArgumentException("维度总权重必须大于零");
        if (knownWeight.signum() == 0) throw new IllegalArgumentException("全部指标缺失，不能生成维度分");
        return new DimensionScore(
                dimension,
                weightedScore.divide(knownWeight, 2, RoundingMode.HALF_UP),
                knownWeight.divide(totalWeight, 2, RoundingMode.HALF_UP),
                factIds,
                missingKeys);
    }

    private void validateFixedFormula(Dimension dimension, List<WeightedMetric> inputs) {
        var expected = FIXED_FORMULAS.get(dimension);
        if (expected == null) return;
        var actual = new java.util.HashMap<String, BigDecimal>();
        for (var input : inputs) actual.put(input.metric().key(), input.weight());
        if (!actual.equals(expected)) throw new IllegalArgumentException(dimension + " 不符合 scoring-v1 固定公式");
    }

    private static Map<String, BigDecimal> weights(String... entries) {
        var result = new LinkedHashMap<String, BigDecimal>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(entries[index], new BigDecimal(entries[index + 1]));
        }
        return Map.copyOf(result);
    }
}
