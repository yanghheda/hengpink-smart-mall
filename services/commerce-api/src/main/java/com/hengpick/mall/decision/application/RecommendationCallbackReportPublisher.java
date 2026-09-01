package com.hengpick.mall.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import com.hengpick.mall.decision.infrastructure.DecisionMapper;
import com.hengpick.mall.recommendation.application.RecommendationReportService;
import com.hengpick.mall.recommendation.domain.AdjustmentContext;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationReportSnapshot;
import com.hengpick.mall.recommendation.domain.RecommendationSnapshot;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 把 Agent 摘要中的 Java ScoreCard 投影为首版权威重算快照。 */
public final class RecommendationCallbackReportPublisher {
    private final DecisionMapper decisionMapper;
    private final RecommendationReportService reportService;
    private final ObjectMapper objectMapper;

    public RecommendationCallbackReportPublisher(
            DecisionMapper decisionMapper,
            RecommendationReportService reportService,
            ObjectMapper objectMapper) {
        this.decisionMapper = decisionMapper;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    public void publish(RunCompletionCallback completion) {
        if (!"REPORT_READY".equals(completion.completionType())) return;
        var context = decisionMapper.findReportPublicationContext(completion.runId());
        if (context == null) throw new CallbackConflictException("报告发布上下文不存在");
        var summary = objectMapper.valueToTree(completion.resultSummary());
        var candidatesBySku = index(summary.path("candidates"), "skuId");
        var pricePlans = summary.path("pricePlans");
        var callbackVersions = summary.path("versions");
        if (!context.datasetVersion().equals(text(callbackVersions, "dataset"))) {
            throw new CallbackConflictException("报告回调数据版本与 Session 不一致");
        }
        var candidates = new ArrayList<RecommendationCandidate>();
        var presentations = new ArrayList<RecommendationReportSnapshot.CandidatePresentation>();
        Map<com.hengpick.mall.recommendation.domain.Dimension, BigDecimal> weights = Map.of();
        for (var item : summary.path("scoreCards")) {
            var scoreCard = convert(item.path("scoreCard"), ScoreCard.class);
            if (weights.isEmpty()) weights = scoreCard.normalizedWeights();
            var finalPrice = decimal(item, "finalPrice");
            candidates.add(new RecommendationCandidate(
                    new ScoreCard(scoreCard.skuId(), scoreCard.capabilityScores(), scoreCard.dimensionScores(),
                            scoreCard.scoringVersion()),
                    scoreCard.confidence().input(), scoreCard.appliedRisks(), finalPrice, List.of()));
            var candidate = candidatesBySku.get(scoreCard.skuId());
            if (candidate == null) throw new CallbackConflictException("ScoreCard 不属于回调候选");
            var plan = pricePlans.path(scoreCard.skuId()).path(0);
            var factIds = scoreCard.dimensionScores().values().stream()
                    .flatMap(value -> value.sourceFactIds().stream()).distinct().limit(10).toList();
            presentations.add(new RecommendationReportSnapshot.CandidatePresentation(
                    text(candidate, "productId"), scoreCard.skuId(), text(plan, "pricePlanId"), true,
                    List.of(new RecommendationReportSnapshot.Reason(
                            "该排序由保存的五维事实确定性计算。", factIds, List.of()))));
        }
        if (candidates.isEmpty()) throw new CallbackConflictException("报告回调缺少 Java ScoreCard");
        var scoring = new com.hengpick.mall.recommendation.domain.RecommendationScorer()
                .score(candidates, weights);
        var snapshot = new RecommendationReportSnapshot(
                new RecommendationSnapshot(1,
                        new AdjustmentContext(BigDecimal.ZERO, Set.of(), weights), candidates, scoring),
                presentations);
        var draftRecommendations = new ArrayList<com.hengpick.mall.decision.report.FinalReportDraft.Recommendation>();
        for (int index = 0; index < scoring.ranked().size(); index++) {
            var scored = scoring.ranked().get(index);
            var presentation = snapshot.presentation(scored.scoreCard().skuId());
            draftRecommendations.add(new com.hengpick.mall.decision.report.FinalReportDraft.Recommendation(
                    index + 1, presentation.productId(), presentation.skuId(), scored.scoreCard().finalScore(),
                    presentation.pricePlanId(), scored.finalPrice(), true,
                    presentation.reasons().stream().map(reason -> new com.hengpick.mall.decision.report.FinalReportDraft.Reason(
                            reason.text(), reason.factIds(), reason.evidenceIds())).toList()));
        }
        var draft = new com.hengpick.mall.decision.report.FinalReportDraft(
                context.datasetVersion(), "已根据权威商品、价格和评分快照生成基础报告。",
                draftRecommendations, List.of());
        var versions = new LinkedHashMap<String, Object>();
        versions.put("datasetVersion", context.datasetVersion());
        versions.put("scoringVersion", text(callbackVersions, "scoring"));
        versions.put("pricingRuleVersion", text(callbackVersions, "pricing"));
        versions.put("promptVersion", text(callbackVersions, "prompt"));
        versions.put("embeddingVersion", text(callbackVersions, "embedding"));
        reportService.publishInitial(context.userId(), context.sessionId(), draft, snapshot, versions);
    }

    private Map<String, JsonNode> index(JsonNode values, String field) {
        var result = new LinkedHashMap<String, JsonNode>();
        for (var value : values) result.put(text(value, field), value);
        return result;
    }

    private String text(JsonNode node, String field) {
        var value = node.path(field).asText();
        if (value.isBlank()) throw new CallbackConflictException("报告回调缺少字段：" + field);
        return value;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        try {
            return new BigDecimal(text(node, field));
        } catch (NumberFormatException exception) {
            throw new CallbackConflictException("报告回调金额字段非法：" + field);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (Exception exception) {
            throw new CallbackConflictException("Java ScoreCard 快照无法解析");
        }
    }
}
