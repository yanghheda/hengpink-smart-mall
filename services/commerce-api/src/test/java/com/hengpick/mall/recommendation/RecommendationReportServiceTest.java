package com.hengpick.mall.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.recommendation.application.RecommendationReportService;
import com.hengpick.mall.recommendation.domain.AdjustmentContext;
import com.hengpick.mall.recommendation.domain.ConfidenceInput;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScore;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationReportRepository;
import com.hengpick.mall.recommendation.domain.RecommendationReportSnapshot;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.RecommendationSnapshot;
import com.hengpick.mall.recommendation.domain.ReportVersionConflictException;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import com.hengpick.mall.recommendation.domain.StoredRecommendationReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void reweightUsesSavedSnapshotAndAtomicallyCreatesNextVersion() {
        var repository = new InMemoryRepository(initialReport());
        var service = new RecommendationReportService(repository, new RecommendationScorer(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.reweight("USER-1", "SESSION-1", 1,
                Map.of(Dimension.PRICE_VALUE, BigDecimal.ONE));

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.selectedSkuId()).isEqualTo("SKU-CHEAP");
        assertThat(repository.current.snapshot().recommendation().reportVersion()).isEqualTo(2);
        assertThat(repository.current.report()).containsEntry("generationType", "DETERMINISTIC_REWEIGHT");
    }

    @Test
    void staleVersionCannotOverwriteCurrentReport() {
        var service = new RecommendationReportService(new InMemoryRepository(initialReport()),
                new RecommendationScorer(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.reweight("USER-1", "SESSION-1", 0, Map.of()))
                .isInstanceOf(ReportVersionConflictException.class);
    }

    @Test
    void anotherUserCannotDiscoverOrReweightTheSession() {
        var service = new RecommendationReportService(new InMemoryRepository(initialReport()),
                new RecommendationScorer(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.reweight("USER-2", "SESSION-1", 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("决策报告不存在");
    }

    @Test
    void zeroWeightsRestoreJavaDefaultsWithoutAnyAgentDependency() {
        var service = new RecommendationReportService(new InMemoryRepository(initialReport()),
                new RecommendationScorer(), Clock.fixed(NOW, ZoneOffset.UTC));
        var zeros = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        for (var dimension : Dimension.values()) zeros.put(dimension, BigDecimal.ZERO);

        var result = service.reweight("USER-1", "SESSION-1", 1, zeros);

        assertThat(result.weights()).containsEntry(Dimension.NEED_MATCH, new BigDecimal("0.35"));
    }

    private StoredRecommendationReport initialReport() {
        var scorer = new RecommendationScorer();
        var candidates = List.of(candidate("SKU-FIT", "95", "60", "3000"),
                candidate("SKU-CHEAP", "70", "100", "2000"));
        var weights = Map.of(
                Dimension.NEED_MATCH, new BigDecimal("0.35"),
                Dimension.PRICE_VALUE, new BigDecimal("0.25"),
                Dimension.REVIEW_QUALITY, new BigDecimal("0.20"),
                Dimension.PROMOTION_VALUE, new BigDecimal("0.10"),
                Dimension.RELIABILITY, new BigDecimal("0.10"));
        var context = new AdjustmentContext(new BigDecimal("3000"), Set.of(), weights);
        var scoring = new RecommendationSnapshot(1, context, candidates, scorer.score(candidates, weights));
        var reason = new RecommendationReportSnapshot.Reason("确定性理由", List.of("FACT-1"), List.of());
        var snapshot = new RecommendationReportSnapshot(scoring, List.of(
                new RecommendationReportSnapshot.CandidatePresentation(
                        "PRODUCT-1", "SKU-FIT", "PLAN-1", true, List.of(reason)),
                new RecommendationReportSnapshot.CandidatePresentation(
                        "PRODUCT-2", "SKU-CHEAP", "PLAN-2", true, List.of(reason))));
        return new StoredRecommendationReport("SESSION-1", "USER-1", 1, "SKU-FIT",
                Map.of("generationType", "REALTIME_AI"), Map.of("datasetVersion", "D1"), snapshot, NOW);
    }

    private RecommendationCandidate candidate(String skuId, String need, String price, String finalPrice) {
        var dimensions = new EnumMap<Dimension, DimensionScore>(Dimension.class);
        for (var dimension : Dimension.values()) {
            var value = dimension == Dimension.NEED_MATCH ? need
                    : dimension == Dimension.PRICE_VALUE ? price : "80";
            dimensions.put(dimension, new DimensionScore(
                    dimension, new BigDecimal(value), BigDecimal.ONE, List.of("FACT-1"), List.of()));
        }
        return new RecommendationCandidate(new ScoreCard(skuId, List.of(), dimensions, "scoring-v1"),
                new ConfidenceInput("1", "1", "1", "1", "0", false), List.of(),
                new BigDecimal(finalPrice), List.of());
    }

    private static final class InMemoryRepository implements RecommendationReportRepository {
        private StoredRecommendationReport current;

        private InMemoryRepository(StoredRecommendationReport current) { this.current = current; }

        @Override public Optional<StoredRecommendationReport> findCurrent(String userId, String sessionId) {
            return current.userId().equals(userId) && current.sessionId().equals(sessionId)
                    ? Optional.of(current) : Optional.empty();
        }
        @Override public void publishInitial(StoredRecommendationReport report) { current = report; }
        @Override public boolean appendReweighted(StoredRecommendationReport report, int expectedVersion) {
            if (current.version() != expectedVersion) return false;
            current = report;
            return true;
        }
    }
}
