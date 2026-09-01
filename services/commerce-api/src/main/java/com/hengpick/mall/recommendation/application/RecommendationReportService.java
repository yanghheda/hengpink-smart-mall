package com.hengpick.mall.recommendation.application;

import com.hengpick.mall.decision.report.FinalReportDraft;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.RecommendationAdjustmentService;
import com.hengpick.mall.recommendation.domain.RecommendationReportRepository;
import com.hengpick.mall.recommendation.domain.RecommendationReportSnapshot;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.ReportVersionConflictException;
import com.hengpick.mall.recommendation.domain.StoredRecommendationReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 发布权威报告，并只用同版本 Java 快照执行权重重算。 */
public final class RecommendationReportService implements RecommendationReweightUseCase {
    private final RecommendationReportRepository repository;
    private final RecommendationAdjustmentService adjustmentService;
    private final Clock clock;

    public RecommendationReportService(
            RecommendationReportRepository repository, RecommendationScorer scorer, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.adjustmentService = new RecommendationAdjustmentService(Objects.requireNonNull(scorer));
        this.clock = Objects.requireNonNull(clock);
    }

    public StoredRecommendationReport publishInitial(
            String userId,
            String sessionId,
            FinalReportDraft draft,
            RecommendationReportSnapshot snapshot,
            Map<String, Object> versions) {
        if (snapshot.recommendation().reportVersion() != 1) {
            throw new IllegalArgumentException("初始推荐快照版本必须为 1");
        }
        var topSkuId = snapshot.recommendation().result().ranked().getFirst().scoreCard().skuId();
        var report = new StoredRecommendationReport(sessionId, userId, 1, topSkuId,
                initialProjection(draft), versions, snapshot, clock.instant());
        repository.publishInitial(report);
        return report;
    }

    @Override
    public ReweightResult reweight(
            String userId,
            String sessionId,
            int expectedReportVersion,
            Map<Dimension, BigDecimal> requestedWeights) {
        var current = repository.findCurrent(userId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("决策报告不存在"));
        var updatedScoring = adjustmentService.reweight(
                current.snapshot().recommendation(), expectedReportVersion, requestedWeights);
        var updatedSnapshot = new RecommendationReportSnapshot(
                updatedScoring, current.snapshot().presentations());
        var projection = reweightedProjection(updatedSnapshot);
        var selectedSkuId = updatedScoring.result().ranked().getFirst().scoreCard().skuId();
        var next = new StoredRecommendationReport(sessionId, userId, Math.toIntExact(updatedScoring.reportVersion()),
                selectedSkuId, projection, current.versions(), updatedSnapshot, clock.instant());
        if (!repository.appendReweighted(next, expectedReportVersion)) {
            throw new ReportVersionConflictException(expectedReportVersion, expectedReportVersion + 1L);
        }
        return result(next);
    }

    private Map<String, Object> initialProjection(FinalReportDraft draft) {
        var recommendations = draft.recommendations().stream().map(item -> Map.<String, Object>of(
                "rank", item.rank(), "productId", item.productId(), "skuId", item.skuId(),
                "finalScore", item.finalScore().toPlainString(), "pricePlanId", item.pricePlanId(),
                "finalPrice", item.finalPrice().toPlainString(), "simulated", item.simulated(),
                "reasons", item.reasons())).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("summary", draft.summary());
        result.put("recommendations", recommendations);
        result.put("overallDataGaps", draft.overallDataGaps());
        result.put("generationType", "VALIDATED_REPORT");
        return result;
    }

    private Map<String, Object> reweightedProjection(RecommendationReportSnapshot snapshot) {
        var recommendations = new ArrayList<Map<String, Object>>();
        var ranked = snapshot.recommendation().result().ranked();
        for (int index = 0; index < Math.min(3, ranked.size()); index++) {
            var scored = ranked.get(index);
            var presentation = snapshot.presentation(scored.scoreCard().skuId());
            var item = new LinkedHashMap<String, Object>();
            item.put("rank", index + 1);
            item.put("productId", presentation.productId());
            item.put("skuId", presentation.skuId());
            item.put("finalScore", scored.scoreCard().finalScore().toPlainString());
            item.put("pricePlanId", presentation.pricePlanId());
            item.put("finalPrice", scored.finalPrice().toPlainString());
            item.put("simulated", presentation.simulated());
            item.put("reasons", presentation.reasons());
            recommendations.add(item);
        }
        return Map.of(
                "summary", "已按新权重使用保存的维度分重新排序。",
                "recommendations", recommendations,
                "overallDataGaps", List.of(),
                "generationType", "DETERMINISTIC_REWEIGHT");
    }

    private ReweightResult result(StoredRecommendationReport report) {
        var ranked = report.snapshot().recommendation().result().ranked();
        var recommendations = new ArrayList<ReweightResult.RankedCandidate>();
        for (int index = 0; index < Math.min(3, ranked.size()); index++) {
            var item = ranked.get(index);
            var presentation = report.snapshot().presentation(item.scoreCard().skuId());
            recommendations.add(new ReweightResult.RankedCandidate(index + 1, presentation.productId(),
                    presentation.skuId(), item.scoreCard().finalScore(), presentation.pricePlanId(),
                    item.finalPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        }
        var weights = ranked.getFirst().scoreCard().normalizedWeights();
        return new ReweightResult(report.sessionId(), report.version(), report.selectedSkuId(),
                weights, List.copyOf(recommendations), "DETERMINISTIC_REWEIGHT");
    }
}
