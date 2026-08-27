package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationScorer {

    private static final BigDecimal RISK_CAP = new BigDecimal("15");
    private static final BigDecimal CRITICAL_MISSING_CONFIDENCE_CAP = new BigDecimal("0.79");
    private static final Map<Dimension, BigDecimal> DEFAULT_WEIGHTS = Map.of(
            Dimension.NEED_MATCH, new BigDecimal("0.35"),
            Dimension.PRICE_VALUE, new BigDecimal("0.25"),
            Dimension.REVIEW_QUALITY, new BigDecimal("0.20"),
            Dimension.PROMOTION_VALUE, new BigDecimal("0.10"),
            Dimension.RELIABILITY, new BigDecimal("0.10"));

    private static final Comparator<ScoredRecommendation> RANKING = Comparator
            .comparing((ScoredRecommendation item) -> item.scoreCard().finalScore()).reversed()
            .thenComparing(item -> item.scoreCard().dimensionScores().get(Dimension.NEED_MATCH).score(),
                    Comparator.reverseOrder())
            .thenComparing(item -> item.scoreCard().confidence().score(), Comparator.reverseOrder())
            .thenComparing(ScoredRecommendation::finalPrice)
            .thenComparing(item -> item.scoreCard().skuId());

    public RecommendationResult score(
            List<RecommendationCandidate> candidates,
            Map<Dimension, BigDecimal> requestedWeights) {
        var weights = normalizeWeights(requestedWeights);
        var ranked = new ArrayList<ScoredRecommendation>();
        var rejected = new ArrayList<ScoredRecommendation>();
        for (var candidate : candidates) {
            if (candidate.rejected()) {
                rejected.add(new ScoredRecommendation(
                        RecommendationStatus.REJECTED,
                        candidate.rawScoreCard(),
                        candidate.finalPrice(),
                        candidate.rejectionReasonCodes()));
                continue;
            }
            ranked.add(scoreCandidate(candidate, weights));
        }
        ranked.sort(RANKING);
        return new RecommendationResult(ranked, rejected);
    }

    private ScoredRecommendation scoreCandidate(
            RecommendationCandidate candidate,
            Map<Dimension, BigDecimal> weights) {
        var rawCard = candidate.rawScoreCard();
        var baseScore = BigDecimal.ZERO;
        for (var dimension : Dimension.values()) {
            var dimensionScore = rawCard.dimensionScores().get(dimension);
            if (dimensionScore == null) throw new IllegalArgumentException("评分卡缺少顶层维度：" + dimension);
            baseScore = baseScore.add(dimensionScore.score().multiply(weights.get(dimension)));
        }
        baseScore = baseScore.setScale(2, RoundingMode.HALF_UP);

        var confidence = calculateConfidence(candidate.confidenceInput());
        var coefficient = new BigDecimal("0.75")
                .add(new BigDecimal("0.25").multiply(confidence.score()))
                .setScale(4, RoundingMode.HALF_UP);
        var appliedRisks = deduplicateRisks(candidate.risks());
        var riskPenalty = appliedRisks.stream()
                .map(RiskItem::penalty)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(RISK_CAP);
        var finalScore = clamp(baseScore.multiply(coefficient).subtract(riskPenalty),
                BigDecimal.ZERO, new BigDecimal("100"));

        var scoreCard = new ScoreCard(
                rawCard.skuId(), rawCard.capabilityScores(), rawCard.dimensionScores(), rawCard.scoringVersion(),
                weights, baseScore, confidence, coefficient, appliedRisks, riskPenalty, finalScore);
        return new ScoredRecommendation(
                RecommendationStatus.RANKED, scoreCard, candidate.finalPrice(), List.of());
    }

    private Map<Dimension, BigDecimal> normalizeWeights(Map<Dimension, BigDecimal> requestedWeights) {
        var sanitized = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        for (var dimension : Dimension.values()) {
            var value = requestedWeights.getOrDefault(dimension, BigDecimal.ZERO);
            if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("顶层权重不能为空或为负数");
            }
            sanitized.put(dimension, value);
        }
        var sum = sanitized.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() == 0) return DEFAULT_WEIGHTS;
        var normalized = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        var assigned = BigDecimal.ZERO;
        for (var entry : sanitized.entrySet()) {
            var weight = entry.getKey() == Dimension.RELIABILITY
                    ? BigDecimal.ONE.subtract(assigned)
                    : entry.getValue().divide(sum, 12, RoundingMode.HALF_UP);
            normalized.put(entry.getKey(), weight);
            assigned = assigned.add(weight);
        }
        return Map.copyOf(normalized);
    }

    private ConfidenceScore calculateConfidence(ConfidenceInput input) {
        var score = input.dataCompleteness().multiply(new BigDecimal("0.30"))
                .add(input.evidenceCoverage().multiply(new BigDecimal("0.30")))
                .add(input.skuMatchCertainty().multiply(new BigDecimal("0.20")))
                .add(input.pricingCertainty().multiply(new BigDecimal("0.20")))
                .subtract(input.conflictPenalty());
        score = clamp(score, BigDecimal.ZERO, BigDecimal.ONE);
        if (input.criticalAttributeMissing()) score = score.min(CRITICAL_MISSING_CONFIDENCE_CAP);
        score = score.setScale(2, RoundingMode.HALF_UP);
        var level = score.compareTo(new BigDecimal("0.80")) >= 0
                ? ConfidenceLevel.HIGH
                : score.compareTo(new BigDecimal("0.60")) >= 0
                        ? ConfidenceLevel.MEDIUM
                        : ConfidenceLevel.LOW;
        return new ConfidenceScore(score, level, input);
    }

    private List<RiskItem> deduplicateRisks(List<RiskItem> risks) {
        var byCause = new LinkedHashMap<String, RiskItem>();
        for (var risk : risks) {
            byCause.merge(risk.causeId(), risk,
                    (left, right) -> left.penalty().compareTo(right.penalty()) >= 0 ? left : right);
        }
        return List.copyOf(byCause.values());
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.max(minimum).min(maximum);
    }
}
