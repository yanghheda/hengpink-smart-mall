package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationAdjustmentService {

    private static final BigDecimal BUDGET_STEP = new BigDecimal("100");
    private static final int MAX_BUDGET_STEPS = 10;
    private static final BigDecimal WEIGHT_STEP = new BigDecimal("0.05");
    private static final int MAX_WEIGHT_STEPS = 20;

    private final RecommendationScorer scorer;

    public RecommendationAdjustmentService(RecommendationScorer scorer) {
        this.scorer = scorer;
    }

    public RecommendationSnapshot reweight(
            RecommendationSnapshot current,
            long expectedReportVersion,
            Map<Dimension, BigDecimal> requestedWeights) {
        if (expectedReportVersion != current.reportVersion()) {
            throw new ReportVersionConflictException(expectedReportVersion, current.reportVersion());
        }
        var result = scorer.score(current.candidates(), requestedWeights);
        var normalizedWeights = result.ranked().isEmpty()
                ? requestedWeights
                : result.ranked().getFirst().scoreCard().normalizedWeights();
        var context = current.context().withWeights(normalizedWeights);
        return new RecommendationSnapshot(current.reportVersion() + 1, context, current.candidates(), result);
    }

    public List<Counterfactual> searchCounterfactuals(
            RecommendationSnapshot baseline,
            Set<String> searchableMemberships,
            CandidateRecalculator candidateRecalculator) {
        var before = topOne(baseline.result());
        if (before == null) return List.of();

        var results = new ArrayList<Counterfactual>();
        findBudget(baseline, before, candidateRecalculator).ifPresent(results::add);
        findMembership(baseline, before, searchableMemberships, candidateRecalculator).ifPresent(results::add);
        findWeight(baseline, before).ifPresent(results::add);
        return List.copyOf(results.stream().limit(3).toList());
    }

    private java.util.Optional<Counterfactual> findBudget(
            RecommendationSnapshot baseline,
            ScoredRecommendation before,
            CandidateRecalculator candidateRecalculator) {
        for (int step = 1; step <= MAX_BUDGET_STEPS; step++) {
            var delta = BUDGET_STEP.multiply(BigDecimal.valueOf(step));
            for (var value : List.of(
                    baseline.context().budgetLimit().subtract(delta),
                    baseline.context().budgetLimit().add(delta))) {
                if (value.compareTo(BigDecimal.ZERO) < 0) continue;
                var context = baseline.context().withBudgetLimit(value);
                var verified = verify(baseline, before, context, candidateRecalculator.recalculate(context), null,
                        CounterfactualType.BUDGET);
                if (verified.isPresent()) return verified;
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Counterfactual> findMembership(
            RecommendationSnapshot baseline,
            ScoredRecommendation before,
            Set<String> searchableMemberships,
            CandidateRecalculator candidateRecalculator) {
        for (var membership : searchableMemberships.stream().sorted().toList()) {
            var changed = new HashSet<>(baseline.context().memberships());
            if (!changed.add(membership)) changed.remove(membership);
            var context = baseline.context().withMemberships(changed);
            var verified = verify(baseline, before, context, candidateRecalculator.recalculate(context), null,
                    CounterfactualType.MEMBERSHIP);
            if (verified.isPresent()) return verified;
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Counterfactual> findWeight(
            RecommendationSnapshot baseline,
            ScoredRecommendation before) {
        for (int step = 1; step <= MAX_WEIGHT_STEPS; step++) {
            var delta = WEIGHT_STEP.multiply(BigDecimal.valueOf(step));
            for (var dimension : Dimension.values()) {
                for (var value : List.of(
                        baseline.context().weights().getOrDefault(dimension, BigDecimal.ZERO).subtract(delta),
                        baseline.context().weights().getOrDefault(dimension, BigDecimal.ZERO).add(delta))) {
                    if (value.compareTo(BigDecimal.ZERO) < 0) continue;
                    var weights = new EnumMap<Dimension, BigDecimal>(Dimension.class);
                    weights.putAll(baseline.context().weights());
                    weights.put(dimension, value);
                    var context = baseline.context().withWeights(weights);
                    var verified = verify(baseline, before, context, baseline.candidates(), dimension,
                            CounterfactualType.WEIGHT);
                    if (verified.isPresent()) return verified;
                }
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Counterfactual> verify(
            RecommendationSnapshot baseline,
            ScoredRecommendation before,
            AdjustmentContext afterContext,
            List<RecommendationCandidate> candidates,
            Dimension changedDimension,
            CounterfactualType type) {
        var result = scorer.score(candidates, afterContext.weights());
        var after = topOne(result);
        if (after == null || before.scoreCard().skuId().equals(after.scoreCard().skuId())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Counterfactual(
                type, changedDimension, baseline.context(), afterContext,
                before.scoreCard().skuId(), after.scoreCard().skuId(),
                before.scoreCard(), after.scoreCard()));
    }

    private ScoredRecommendation topOne(RecommendationResult result) {
        return result.ranked().isEmpty() ? null : result.ranked().getFirst();
    }
}
