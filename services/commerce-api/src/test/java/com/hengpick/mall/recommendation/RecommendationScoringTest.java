package com.hengpick.mall.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.recommendation.domain.ConfidenceInput;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScore;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.RecommendationStatus;
import com.hengpick.mall.recommendation.domain.RiskItem;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationScoringTest {

    private final RecommendationScorer scorer = new RecommendationScorer();

    @Test
    void hardConstraintFailureCannotBeRescuedByPerfectScores() {
        var rejected = candidate("SKU-REJECTED", "100", "99.00", true, "1", List.of());
        var matched = candidate("SKU-MATCHED", "60", "199.00", false, "1", List.of());

        var result = scorer.score(
                List.of(rejected, matched),
                Map.of(Dimension.NEED_MATCH, new BigDecimal("1")));

        assertThat(result.ranked()).extracting(item -> item.scoreCard().skuId())
                .containsExactly("SKU-MATCHED");
        assertThat(result.rejected()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(RecommendationStatus.REJECTED);
            assertThat(item.rejectionReasonCodes()).containsExactly("BUDGET_EXCEEDED");
            assertThat(item.scoreCard().finalScore()).isNull();
        });
    }

    @Test
    void zeroWeightsRestoreDefaultsAndFinalScoreIsReproducible() {
        var result = scorer.score(
                List.of(candidate("SKU-1", "80", "2999.00", false, "0.8", List.of())),
                zeroWeights());

        var card = result.ranked().getFirst().scoreCard();
        assertThat(card.normalizedWeights()).containsEntry(Dimension.NEED_MATCH, new BigDecimal("0.35"));
        assertThat(card.normalizedWeights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1.00");
        assertThat(card.baseScore()).isEqualByComparingTo("80.00");
        assertThat(card.confidence().score()).isEqualByComparingTo("0.80");
        assertThat(card.confidenceCoefficient()).isEqualByComparingTo("0.9500");
        assertThat(card.finalScore()).isEqualByComparingTo("76.000000");
    }

    @Test
    void criticalAttributeMissingPreventsHighConfidence() {
        var confidence = new ConfidenceInput("1", "1", "1", "1", "0", true);
        var result = scorer.score(
                List.of(candidate("SKU-1", "90", "2999.00", false, confidence, List.of())),
                Map.of(Dimension.NEED_MATCH, BigDecimal.ONE));

        assertThat(result.ranked().getFirst().scoreCard().confidence().score())
                .isEqualByComparingTo("0.79");
        assertThat(result.ranked().getFirst().scoreCard().confidence().level().name()).isEqualTo("MEDIUM");
    }

    @Test
    void risksAreDeduplicatedByCauseAndCappedAtFifteen() {
        var risks = List.of(
                new RiskItem("MISSING_ATTRIBUTE", "battery", "4"),
                new RiskItem("MISSING_ATTRIBUTE_REPEATED", "battery", "5"),
                new RiskItem("AFTER_SALES", "service", "12"));

        var card = scorer.score(
                        List.of(candidate("SKU-1", "100", "2999.00", false, "1", risks)),
                        Map.of(Dimension.NEED_MATCH, BigDecimal.ONE))
                .ranked().getFirst().scoreCard();

        assertThat(card.appliedRisks()).extracting(RiskItem::code)
                .containsExactly("MISSING_ATTRIBUTE_REPEATED", "AFTER_SALES");
        assertThat(card.riskPenalty()).isEqualByComparingTo("15");
        assertThat(card.finalScore()).isEqualByComparingTo("85.000000");
    }

    @Test
    void exactTiesUseNeedMatchConfidencePriceAndSkuIdInOrder() {
        var highNeed = candidateWithDimensions("SKU-Z", "90", "75", "2999.00", "1");
        var highConfidence = candidateWithDimensions("SKU-Y", "80", "75", "2999.00", "1");
        var cheap = candidateWithDimensions("SKU-B", "80", "100", "1999.00", "0");
        var stableId = candidateWithDimensions("SKU-A", "80", "100", "1999.00", "0");

        var result = scorer.score(
                List.of(highConfidence, stableId, highNeed, cheap),
                Map.of(Dimension.PRICE_VALUE, BigDecimal.ONE));

        assertThat(result.ranked()).extracting(item -> item.scoreCard().skuId())
                .containsExactly("SKU-Z", "SKU-Y", "SKU-A", "SKU-B");
    }

    private RecommendationCandidate candidate(
            String skuId,
            String score,
            String finalPrice,
            boolean rejected,
            String confidence,
            List<RiskItem> risks) {
        return candidate(skuId, score, finalPrice, rejected,
                new ConfidenceInput(confidence, confidence, confidence, confidence, "0", false), risks);
    }

    private RecommendationCandidate candidate(
            String skuId,
            String score,
            String finalPrice,
            boolean rejected,
            ConfidenceInput confidence,
            List<RiskItem> risks) {
        var reasons = rejected ? List.of("BUDGET_EXCEEDED") : List.<String>of();
        return new RecommendationCandidate(rawCard(skuId, score, score), confidence, risks,
                new BigDecimal(finalPrice), reasons);
    }

    private RecommendationCandidate candidateWithDimensions(
            String skuId, String needMatch, String priceValue, String finalPrice, String confidence) {
        return new RecommendationCandidate(rawCard(skuId, needMatch, priceValue),
                new ConfidenceInput(confidence, confidence, confidence, confidence, "0", false),
                List.of(), new BigDecimal(finalPrice), List.of());
    }

    private ScoreCard rawCard(String skuId, String needMatch, String otherScore) {
        var dimensions = new EnumMap<Dimension, DimensionScore>(Dimension.class);
        for (var dimension : Dimension.values()) {
            var score = dimension == Dimension.NEED_MATCH ? needMatch : otherScore;
            dimensions.put(dimension, new DimensionScore(
                    dimension, new BigDecimal(score), BigDecimal.ONE, List.of("FACT-" + dimension), List.of()));
        }
        return new ScoreCard(skuId, List.of(), dimensions, "scoring-v1");
    }

    private Map<Dimension, BigDecimal> zeroWeights() {
        var weights = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        for (var dimension : Dimension.values()) weights.put(dimension, BigDecimal.ZERO);
        return weights;
    }
}
