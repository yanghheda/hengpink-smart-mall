package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.application.RecommendationCallbackReportPublisher;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import com.hengpick.mall.decision.infrastructure.DecisionMapper;
import com.hengpick.mall.decision.infrastructure.ReportPublicationContextRow;
import com.hengpick.mall.recommendation.application.RecommendationReportService;
import com.hengpick.mall.recommendation.domain.ConfidenceInput;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScore;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationReportRepository;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import com.hengpick.mall.recommendation.domain.StoredRecommendationReport;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecommendationCallbackReportPublisherTest {
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void reportReadyPublishesVersionOneFromJavaScoreCardAndServerPrice() {
        var repository = new InMemoryRepository();
        var scorer = new RecommendationScorer();
        var service = new RecommendationReportService(repository, scorer, Clock.fixed(NOW, ZoneOffset.UTC));
        var mapper = (DecisionMapper) Proxy.newProxyInstance(
                DecisionMapper.class.getClassLoader(), new Class<?>[] {DecisionMapper.class},
                (proxy, method, arguments) -> method.getName().equals("findReportPublicationContext")
                        ? new ReportPublicationContextRow("SESSION-1", "USER-1", "DATASET-1")
                        : null);
        var publisher = new RecommendationCallbackReportPublisher(mapper, service,
                new ObjectMapper().findAndRegisterModules());
        var candidate = candidate();
        var scored = scorer.score(List.of(candidate), Map.of()).ranked().getFirst();
        var summary = Map.<String, Object>of(
                "candidates", List.of(Map.of("productId", "PRODUCT-1", "skuId", "SKU-1")),
                "scoreCards", List.of(Map.of(
                        "skuId", "SKU-1", "finalPrice", "2999.00", "scoreCard", scored.scoreCard())),
                "pricePlans", Map.of("SKU-1", List.of(Map.of(
                        "pricePlanId", "PLAN-1", "finalPrice", "2999.00"))),
                "evidence", Map.of("SKU-1", List.of(Map.of("evidence_id", "EV-SKU-1"))),
                "reportNarrative", Map.of(
                        "summary", "这款商品更符合你的核心需求。",
                        "recommendations", List.of(Map.of(
                                "reasons", List.of(Map.of(
                                        "text", "价格与需求匹配度较好。",
                                        "fact_ids", List.of(),
                                        "evidence_ids", List.of("EV-SKU-1")))))),
                "versions", Map.of(
                        "dataset", "DATASET-1", "scoring", "scoring-v1", "pricing", "pricing-v1",
                        "prompt", "intent-v1", "embedding", "stub-embedding-v1"));

        publisher.publish(new RunCompletionCallback(
                "RUN-1", 1, "REPORT_READY", "hash", summary, NOW));

        assertThat(repository.current).isNotNull();
        assertThat(repository.current.version()).isEqualTo(1);
        assertThat(repository.current.selectedSkuId()).isEqualTo("SKU-1");
        assertThat(repository.current.snapshot().recommendation().candidates().getFirst().finalPrice())
                .isEqualByComparingTo("2999.00");
        assertThat(repository.current.snapshot().presentation("SKU-1").reasons().getFirst().text())
                .isEqualTo("价格与需求匹配度较好。");
    }

    private RecommendationCandidate candidate() {
        var dimensions = new EnumMap<Dimension, DimensionScore>(Dimension.class);
        for (var dimension : Dimension.values()) {
            dimensions.put(dimension, new DimensionScore(
                    dimension, new BigDecimal("80"), BigDecimal.ONE,
                    List.of("FACT-SKU-1-" + dimension), List.of()));
        }
        return new RecommendationCandidate(new ScoreCard("SKU-1", List.of(), dimensions, "scoring-v1"),
                new ConfidenceInput("1", "1", "1", "1", "0", false), List.of(),
                new BigDecimal("2999.00"), List.of());
    }

    private static final class InMemoryRepository implements RecommendationReportRepository {
        private StoredRecommendationReport current;

        @Override public Optional<StoredRecommendationReport> findCurrent(String userId, String sessionId) {
            return Optional.ofNullable(current);
        }
        @Override public void publishInitial(StoredRecommendationReport report) { current = report; }
        @Override public boolean appendReweighted(StoredRecommendationReport report, int expectedVersion) {
            current = report;
            return true;
        }
    }
}
