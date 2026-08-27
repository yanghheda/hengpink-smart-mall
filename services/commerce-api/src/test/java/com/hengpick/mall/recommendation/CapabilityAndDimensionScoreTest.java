package com.hengpick.mall.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.recommendation.domain.CapabilityScore;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScoreCalculator;
import com.hengpick.mall.recommendation.domain.MetricFact;
import com.hengpick.mall.recommendation.domain.PhoneCapabilityCalculator;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import com.hengpick.mall.recommendation.domain.WeightedMetric;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityAndDimensionScoreTest {

    private final PhoneCapabilityCalculator capabilityCalculator = new PhoneCapabilityCalculator();
    private final DimensionScoreCalculator dimensionCalculator = new DimensionScoreCalculator();

    @Test
    void missingCapabilityMetricIsRenormalizedInsteadOfTreatedAsZero() {
        var score = capabilityCalculator.battery(List.of(
                known("batteryCapacity", "90", "SKU-1:batteryMah"),
                known("chipEfficiency", "80", "SKU-1:chipEfficiency"),
                missing("charging", "SKU-1:chargingW"),
                known("batteryEvidence", "70", "EV-SKU-1-BATTERY")));

        assertThat(score.score()).isEqualByComparingTo("84.38");
        assertThat(score.completeness()).isEqualByComparingTo("0.80");
        assertThat(score.formulaVersion()).isEqualTo("phone-capability-v1");
        assertThat(score.sourceFactIds())
                .containsExactly("SKU-1:batteryMah", "SKU-1:chipEfficiency", "EV-SKU-1-BATTERY");
        assertThat(score.missingMetricKeys()).containsExactly("charging");
    }

    @Test
    void capabilityWithNoKnownMetricRefusesToInventAZeroScore() {
        assertThatThrownBy(() -> capabilityCalculator.easyUse(List.of(
                        missing("simpleMode", "SKU-1:simpleMode"),
                        missing("largeFontMode", "SKU-1:largeFontMode"),
                        missing("accessibility", "SKU-1:accessibilityScore"),
                        missing("adCleanliness", "SKU-1:adBloatLevel"),
                        missing("systemSupport", "SKU-1:systemSupportYears"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能生成能力分");
    }

    @Test
    void scoreCardKeepsFiveRawDimensionsFactsAndFormulaVersions() {
        CapabilityScore battery = capabilityCalculator.battery(List.of(
                known("batteryCapacity", "90", "SKU-1:batteryMah"),
                known("chipEfficiency", "80", "SKU-1:chipEfficiency"),
                known("charging", "70", "SKU-1:chargingW"),
                known("batteryEvidence", "60", "EV-SKU-1-BATTERY")));

        Map<Dimension, List<WeightedMetric>> inputs = new EnumMap<>(Dimension.class);
        inputs.put(Dimension.NEED_MATCH, List.of(
                new WeightedMetric(known("battery", battery.score().toPlainString(), "CAP-SKU-1-BATTERY"), "0.7"),
                new WeightedMetric(known("easyUse", "80", "CAP-SKU-1-EASY"), "0.3")));
        inputs.put(Dimension.PRICE_VALUE, List.of(
                weighted("pricePercentile", "88", "FACT-SKU-1-PRICE-PERCENTILE", "0.50"),
                weighted("configurationValue", "76", "FACT-SKU-1-CONFIG-VALUE", "0.30"),
                weighted("budgetFit", "90", "FACT-SKU-1-BUDGET-FIT", "0.20")));
        inputs.put(Dimension.REVIEW_QUALITY, List.of(
                weighted("topicSentiment", "82", "EV-SKU-1-SENTIMENT", "0.45"),
                weighted("trustedSampleSize", "70", "EV-SKU-1-SAMPLE", "0.20"),
                weighted("evidenceConsistency", "75", "EV-SKU-1-CONSISTENCY", "0.20"),
                weighted("scenarioRelevance", "90", "EV-SKU-1-RELEVANCE", "0.15")));
        inputs.put(Dimension.PROMOTION_VALUE, List.of(
                weighted("savingRatio", "80", "FACT-SKU-1-SAVING", "0.60"),
                weighted("availability", "100", "FACT-SKU-1-QUALIFICATION", "0.20"),
                weighted("simplicity", "70", "FACT-SKU-1-STEPS", "0.20")));
        inputs.put(Dimension.RELIABILITY, List.of(
                weighted("shopService", "85", "FACT-SHOP-1-SERVICE", "0.30"),
                weighted("afterSalesCoverage", "90", "FACT-SKU-1-AFTER-SALES", "0.30"),
                weighted("warranty", "80", "FACT-SKU-1-WARRANTY", "0.20"),
                weighted("dataCompleteness", "75", "FACT-SKU-1-COMPLETENESS", "0.20")));

        ScoreCard card = dimensionCalculator.calculate("SKU-1", "scoring-v1", List.of(battery), inputs);

        assertThat(card.dimensionScores()).hasSize(5);
        assertThat(card.dimensionScores().get(Dimension.NEED_MATCH).score()).isEqualByComparingTo("80.35");
        assertThat(card.dimensionScores().get(Dimension.PRICE_VALUE).score()).isEqualByComparingTo("84.80");
        assertThat(card.dimensionScores().get(Dimension.REVIEW_QUALITY).sourceFactIds())
                .contains("EV-SKU-1-SENTIMENT", "EV-SKU-1-RELEVANCE");
        assertThat(card.capabilityScores()).singleElement().satisfies(capability ->
                assertThat(capability.formulaVersion()).isEqualTo("phone-capability-v1"));
        assertThat(card.scoringVersion()).isEqualTo("scoring-v1");
    }

    @Test
    void scoreCardRejectsAnIncompleteTopLevelDimensionSet() {
        Map<Dimension, List<WeightedMetric>> inputs = new EnumMap<>(Dimension.class);
        inputs.put(Dimension.NEED_MATCH, List.of(weighted("battery", "80", "CAP-1", "1")));

        assertThatThrownBy(() -> dimensionCalculator.calculate("SKU-1", "scoring-v1", List.of(), inputs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("五个顶层维度");
    }

    @Test
    void fixedDimensionFormulaCannotBeReweightedByTheCaller() {
        Map<Dimension, List<WeightedMetric>> inputs = completeMinimalInputs();
        inputs.put(Dimension.PRICE_VALUE, List.of(
                weighted("pricePercentile", "80", "FACT-PRICE", "1"),
                weighted("configurationValue", "80", "FACT-CONFIG", "0"),
                weighted("budgetFit", "80", "FACT-BUDGET", "0")));

        assertThatThrownBy(() -> dimensionCalculator.calculate("SKU-1", "scoring-v1", List.of(), inputs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scoring-v1 固定公式");
    }

    private Map<Dimension, List<WeightedMetric>> completeMinimalInputs() {
        Map<Dimension, List<WeightedMetric>> inputs = new EnumMap<>(Dimension.class);
        inputs.put(Dimension.NEED_MATCH, List.of(weighted("battery", "80", "CAP-BATTERY", "1")));
        inputs.put(Dimension.PRICE_VALUE, List.of(
                weighted("pricePercentile", "80", "FACT-PRICE", "0.50"),
                weighted("configurationValue", "80", "FACT-CONFIG", "0.30"),
                weighted("budgetFit", "80", "FACT-BUDGET", "0.20")));
        inputs.put(Dimension.REVIEW_QUALITY, List.of(
                weighted("topicSentiment", "80", "EV-SENTIMENT", "0.45"),
                weighted("trustedSampleSize", "80", "EV-SAMPLE", "0.20"),
                weighted("evidenceConsistency", "80", "EV-CONSISTENCY", "0.20"),
                weighted("scenarioRelevance", "80", "EV-RELEVANCE", "0.15")));
        inputs.put(Dimension.PROMOTION_VALUE, List.of(
                weighted("savingRatio", "80", "FACT-SAVING", "0.60"),
                weighted("availability", "80", "FACT-AVAILABLE", "0.20"),
                weighted("simplicity", "80", "FACT-STEPS", "0.20")));
        inputs.put(Dimension.RELIABILITY, List.of(
                weighted("shopService", "80", "FACT-SHOP", "0.30"),
                weighted("afterSalesCoverage", "80", "FACT-AFTER-SALES", "0.30"),
                weighted("warranty", "80", "FACT-WARRANTY", "0.20"),
                weighted("dataCompleteness", "80", "FACT-COMPLETENESS", "0.20")));
        return inputs;
    }

    private WeightedMetric weighted(String key, String score, String factId, String weight) {
        return new WeightedMetric(known(key, score, factId), weight);
    }

    private MetricFact known(String key, String score, String factId) {
        return MetricFact.known(key, score, factId);
    }

    private MetricFact missing(String key, String factId) {
        return MetricFact.missing(key, factId);
    }
}
