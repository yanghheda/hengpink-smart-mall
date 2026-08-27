package com.hengpick.mall.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.recommendation.domain.AdjustmentContext;
import com.hengpick.mall.recommendation.domain.ConfidenceInput;
import com.hengpick.mall.recommendation.domain.CounterfactualType;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScore;
import com.hengpick.mall.recommendation.domain.RecommendationAdjustmentService;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.RecommendationSnapshot;
import com.hengpick.mall.recommendation.domain.ReportVersionConflictException;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationAdjustmentTest {

    private final RecommendationScorer scorer = new RecommendationScorer();
    private final RecommendationAdjustmentService service = new RecommendationAdjustmentService(scorer);

    @Test
    void reweightsStoredDimensionScoresAndCreatesNextReportVersion() {
        var snapshot = snapshot(7, defaultContext(), baseCandidates());

        var updated = service.reweight(snapshot, 7, Map.of(Dimension.PRICE_VALUE, BigDecimal.ONE));

        assertThat(updated.reportVersion()).isEqualTo(8);
        assertThat(updated.result().ranked()).extracting(item -> item.scoreCard().skuId())
                .containsExactly("SKU-CHEAP", "SKU-FIT");
        assertThat(updated.candidates()).isSameAs(snapshot.candidates());
    }

    @Test
    void zeroWeightsRestoreDefaultsDuringVersionedRecalculation() {
        var updated = service.reweight(snapshot(1, defaultContext(), baseCandidates()), 1, zeroWeights());

        assertThat(updated.result().ranked().getFirst().scoreCard().normalizedWeights())
                .containsEntry(Dimension.NEED_MATCH, new BigDecimal("0.35"));
    }

    @Test
    void staleReportVersionCannotOverwriteLatestSnapshot() {
        var snapshot = snapshot(3, defaultContext(), baseCandidates());

        assertThatThrownBy(() -> service.reweight(snapshot, 2, Map.of(Dimension.PRICE_VALUE, BigDecimal.ONE)))
                .isInstanceOf(ReportVersionConflictException.class)
                .hasMessageContaining("期望版本 2")
                .hasMessageContaining("当前版本 3");
    }

    @Test
    void counterfactualsAreMinimalAndVerifiedByRealRecalculation() {
        var snapshot = snapshot(1, defaultContext(), baseCandidates());

        var counterfactuals = service.searchCounterfactuals(
                snapshot,
                Set.of("PLUS"),
                context -> candidatesFor(context));

        assertThat(counterfactuals).hasSize(3);
        assertThat(counterfactuals).extracting(item -> item.type())
                .containsExactly(CounterfactualType.BUDGET, CounterfactualType.MEMBERSHIP, CounterfactualType.WEIGHT);
        assertThat(counterfactuals).allSatisfy(item -> {
            assertThat(item.beforeSkuId()).isEqualTo("SKU-FIT");
            assertThat(item.afterSkuId()).isEqualTo("SKU-CHEAP");
            assertThat(item.beforeScoreCard()).isNotNull();
            assertThat(item.afterScoreCard()).isNotNull();
        });
        assertThat(counterfactuals.get(0).afterContext().budgetLimit()).isEqualByComparingTo("2900");
        assertThat(counterfactuals.get(1).afterContext().memberships()).containsExactly("PLUS");
        assertThat(counterfactuals.get(2).changedDimension()).isEqualTo(Dimension.NEED_MATCH);
        assertThat(counterfactuals.get(2).afterContext().weights().get(Dimension.NEED_MATCH))
                .isEqualByComparingTo("0.15");
    }

    @Test
    void unverifiedPerturbationsAreNotReported() {
        var candidates = List.of(candidate("SKU-ONLY", "90", "90", "2500", List.of()));
        var snapshot = snapshot(1, defaultContext(), candidates);

        var counterfactuals = service.searchCounterfactuals(snapshot, Set.of("PLUS"), context -> candidates);

        assertThat(counterfactuals).isEmpty();
    }

    @Test
    void reweightP95StaysWithinFiveHundredMilliseconds() {
        var snapshot = snapshot(1, defaultContext(), baseCandidates());
        var durations = new ArrayList<Long>();

        for (int index = 0; index < 200; index++) {
            var startedAt = System.nanoTime();
            service.reweight(snapshot, 1, Map.of(Dimension.PRICE_VALUE, BigDecimal.ONE));
            durations.add(System.nanoTime() - startedAt);
        }

        durations.sort(Long::compareTo);
        var p95Nanos = durations.get(189);
        assertThat(p95Nanos).isLessThan(500_000_000L);
    }

    private List<RecommendationCandidate> candidatesFor(AdjustmentContext context) {
        var fitRejected = context.budgetLimit().compareTo(new BigDecimal("3000")) < 0
                ? List.of("BUDGET_EXCEEDED") : List.<String>of();
        var cheapPrice = context.memberships().contains("PLUS") ? "1900" : "2000";
        var cheapPriceScore = context.memberships().contains("PLUS") ? "100" : "82";
        return List.of(
                candidate("SKU-FIT", "92", "65", "3000", fitRejected),
                candidate("SKU-CHEAP", "70", cheapPriceScore, cheapPrice, List.of()));
    }

    private RecommendationSnapshot snapshot(long version, AdjustmentContext context,
            List<RecommendationCandidate> candidates) {
        return new RecommendationSnapshot(version, context, candidates, scorer.score(candidates, context.weights()));
    }

    private AdjustmentContext defaultContext() {
        return new AdjustmentContext(new BigDecimal("3000"), Set.of(), defaultWeights());
    }

    private List<RecommendationCandidate> baseCandidates() {
        return candidatesFor(defaultContext());
    }

    private RecommendationCandidate candidate(
            String skuId, String needMatch, String priceValue, String finalPrice, List<String> rejectionReasons) {
        var dimensions = new EnumMap<Dimension, DimensionScore>(Dimension.class);
        for (var dimension : Dimension.values()) {
            var score = switch (dimension) {
                case NEED_MATCH -> needMatch;
                case PRICE_VALUE -> priceValue;
                default -> "80";
            };
            dimensions.put(dimension, new DimensionScore(
                    dimension, new BigDecimal(score), BigDecimal.ONE, List.of("FACT-" + dimension), List.of()));
        }
        var card = new ScoreCard(skuId, List.of(), dimensions, "scoring-v1");
        var confidence = new ConfidenceInput("1", "1", "1", "1", "0", false);
        return new RecommendationCandidate(card, confidence, List.of(), new BigDecimal(finalPrice), rejectionReasons);
    }

    private Map<Dimension, BigDecimal> defaultWeights() {
        return Map.of(
                Dimension.NEED_MATCH, new BigDecimal("0.35"),
                Dimension.PRICE_VALUE, new BigDecimal("0.25"),
                Dimension.REVIEW_QUALITY, new BigDecimal("0.20"),
                Dimension.PROMOTION_VALUE, new BigDecimal("0.10"),
                Dimension.RELIABILITY, new BigDecimal("0.10"));
    }

    private Map<Dimension, BigDecimal> zeroWeights() {
        var weights = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        for (var dimension : Dimension.values()) weights.put(dimension, BigDecimal.ZERO);
        return weights;
    }
}
