package com.hengpick.mall.recommendation.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 与报告版本绑定、只供 Java 确定性重算的权威快照。 */
public record RecommendationReportSnapshot(
        RecommendationSnapshot recommendation,
        List<CandidatePresentation> presentations) {

    public RecommendationReportSnapshot {
        Objects.requireNonNull(recommendation, "推荐快照不能为空");
        presentations = List.copyOf(presentations);
        var candidateIds = recommendation.candidates().stream()
                .map(item -> item.rawScoreCard().skuId()).collect(java.util.stream.Collectors.toSet());
        var presentationIds = presentations.stream().map(CandidatePresentation::skuId)
                .collect(java.util.stream.Collectors.toSet());
        if (!candidateIds.equals(presentationIds) || presentations.size() != presentationIds.size()) {
            throw new IllegalArgumentException("候选展示快照必须与评分候选一一对应");
        }
    }

    public CandidatePresentation presentation(String skuId) {
        return presentations.stream().filter(item -> item.skuId().equals(skuId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("候选展示快照不存在"));
    }

    public record CandidatePresentation(
            String productId,
            String skuId,
            String pricePlanId,
            boolean simulated,
            List<Reason> reasons) {
        public CandidatePresentation {
            if (productId == null || productId.isBlank() || skuId == null || skuId.isBlank()
                    || pricePlanId == null || pricePlanId.isBlank()) {
                throw new IllegalArgumentException("候选展示标识不能为空");
            }
            reasons = List.copyOf(reasons);
        }
    }

    public record Reason(String text, List<String> factIds, List<String> evidenceIds) {
        public Reason {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("推荐理由不能为空");
            factIds = List.copyOf(factIds);
            evidenceIds = List.copyOf(evidenceIds);
        }
    }
}
