package com.hengpick.mall.decision.report;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 在报告进入持久化边界前，按固定顺序核对所有确定性事实。 */
public final class FinalReportValidator {
    public FinalReportDraft validate(FinalReportDraft draft, ReportValidationContext context) {
        validateSchema(draft, context);
        validateIds(draft, context);
        validateHardConstraints(draft, context);
        validateRanking(draft, context);
        validateAmounts(draft, context);
        validateReferences(draft, context);
        validateVersions(draft, context);
        return draft;
    }

    private void validateSchema(FinalReportDraft draft, ReportValidationContext context) {
        if (draft == null || context == null || isBlank(context.datasetVersion())
                || context.candidates() == null || context.prices() == null || context.facts() == null
                || context.evidence() == null || isBlank(draft.datasetVersion()) || isBlank(draft.summary())
                || draft.recommendations() == null || draft.recommendations().isEmpty()
                || draft.recommendations().size() > 3 || draft.overallDataGaps() == null) {
            fail(ReportValidationStage.SCHEMA, "REPORT_SCHEMA_INVALID", "报告结构不完整");
        }
        var ranks = new HashSet<Integer>();
        for (int index = 0; index < draft.recommendations().size(); index++) {
            var item = draft.recommendations().get(index);
            if (item == null || item.rank() != index + 1 || !ranks.add(item.rank())
                    || isBlank(item.productId()) || isBlank(item.skuId()) || item.finalScore() == null
                    || isBlank(item.pricePlanId()) || item.finalPrice() == null || item.reasons() == null
                    || item.reasons().isEmpty() || item.reasons().size() > 5) {
                fail(ReportValidationStage.SCHEMA, "REPORT_SCHEMA_INVALID", "推荐项结构或连续排名不合法");
            }
            for (var reason : item.reasons()) {
                if (reason == null || isBlank(reason.text()) || reason.factIds() == null
                        || reason.evidenceIds() == null || reason.factIds().size() > 10
                        || reason.evidenceIds().size() > 10
                        || reason.factIds().isEmpty() && reason.evidenceIds().isEmpty()) {
                    fail(ReportValidationStage.SCHEMA, "REPORT_SCHEMA_INVALID", "每条理由必须包含合法引用");
                }
            }
        }
    }

    private void validateIds(FinalReportDraft draft, ReportValidationContext context) {
        for (var item : draft.recommendations()) {
            var candidate = candidate(context, item.skuId());
            if (candidate == null) {
                fail(ReportValidationStage.ID, "CANDIDATE_NOT_FOUND", "推荐 SKU 不在权威候选快照中");
            }
            if (!candidate.productId().equals(item.productId())) {
                fail(ReportValidationStage.ID, "SKU_PRODUCT_MISMATCH", "推荐 SKU 不属于报告商品");
            }
            if (price(context, item.pricePlanId()) == null) {
                fail(ReportValidationStage.ID, "PRICE_PLAN_NOT_FOUND", "价格方案不存在");
            }
        }
    }

    private void validateHardConstraints(FinalReportDraft draft, ReportValidationContext context) {
        for (var item : draft.recommendations()) {
            if (!candidate(context, item.skuId()).hardConstraintsSatisfied()) {
                fail(ReportValidationStage.HARD_CONSTRAINT, "HARD_CONSTRAINT_VIOLATED", "推荐 SKU 违反硬条件");
            }
        }
    }

    private void validateRanking(FinalReportDraft draft, ReportValidationContext context) {
        for (var item : draft.recommendations()) {
            var candidate = candidate(context, item.skuId());
            if (candidate.rank() != item.rank()) {
                fail(ReportValidationStage.RANKING, "RANK_MISMATCH", "推荐顺序与 Java 排序不一致");
            }
            if (!sameNumber(candidate.finalScore(), item.finalScore())) {
                fail(ReportValidationStage.RANKING, "SCORE_MISMATCH", "最终评分与 Java 评分不一致");
            }
        }
    }

    private void validateAmounts(FinalReportDraft draft, ReportValidationContext context) {
        for (var item : draft.recommendations()) {
            var price = price(context, item.pricePlanId());
            if (!price.skuId().equals(item.skuId())) {
                fail(ReportValidationStage.AMOUNT, "PRICE_PLAN_SKU_MISMATCH", "价格方案不属于推荐 SKU");
            }
            if (!sameNumber(price.finalPrice(), item.finalPrice())) {
                fail(ReportValidationStage.AMOUNT, "FINAL_PRICE_MISMATCH", "最终金额与 Java 价格方案不一致");
            }
        }
    }

    private void validateReferences(FinalReportDraft draft, ReportValidationContext context) {
        for (var item : draft.recommendations()) {
            for (var reason : item.reasons()) {
                for (var factId : reason.factIds()) {
                    validateReference(item, reference(context.facts(), factId), "FACT");
                }
                for (var evidenceId : reason.evidenceIds()) {
                    validateReference(item, reference(context.evidence(), evidenceId), "EVIDENCE");
                }
            }
        }
    }

    private void validateReference(
            FinalReportDraft.Recommendation item,
            ReportValidationContext.Reference reference,
            String type) {
        if (reference == null) {
            fail(ReportValidationStage.EVIDENCE, type + "_NOT_FOUND", "理由引用不存在");
        }
        if (!reference.productId().equals(item.productId())
                || reference.skuId() != null && !reference.skuId().equals(item.skuId())) {
            fail(ReportValidationStage.EVIDENCE, type + "_OWNERSHIP_MISMATCH", "理由引用不属于当前候选");
        }
        if (!reference.safe()) {
            fail(ReportValidationStage.EVIDENCE, type + "_UNSAFE", "理由引用未通过安全校验");
        }
    }

    private void validateVersions(FinalReportDraft draft, ReportValidationContext context) {
        if (!Objects.equals(draft.datasetVersion(), context.datasetVersion())) {
            versionFailure();
        }
        for (var item : draft.recommendations()) {
            var candidate = candidate(context, item.skuId());
            var price = price(context, item.pricePlanId());
            if (item.simulated() != candidate.simulated()) {
                fail(ReportValidationStage.VERSION, "SIMULATION_FLAG_MISMATCH", "模拟数据标识与权威候选不一致");
            }
            if (!sameVersion(context.datasetVersion(), candidate.datasetVersion(), price.datasetVersion())) {
                versionFailure();
            }
            for (var reason : item.reasons()) {
                for (var factId : reason.factIds()) {
                    if (!Objects.equals(context.datasetVersion(), reference(context.facts(), factId).datasetVersion())) {
                        versionFailure();
                    }
                }
                for (var evidenceId : reason.evidenceIds()) {
                    if (!Objects.equals(context.datasetVersion(), reference(context.evidence(), evidenceId).datasetVersion())) {
                        versionFailure();
                    }
                }
            }
        }
    }

    private boolean sameVersion(String expected, String... versions) {
        for (var version : versions) {
            if (!Objects.equals(expected, version)) {
                return false;
            }
        }
        return true;
    }

    private ReportValidationContext.Candidate candidate(ReportValidationContext context, String skuId) {
        return context.candidates().stream().filter(item -> Objects.equals(item.skuId(), skuId)).findFirst().orElse(null);
    }

    private ReportValidationContext.Price price(ReportValidationContext context, String pricePlanId) {
        return context.prices().stream().filter(item -> Objects.equals(item.pricePlanId(), pricePlanId)).findFirst().orElse(null);
    }

    private ReportValidationContext.Reference reference(List<ReportValidationContext.Reference> references, String id) {
        return references.stream().filter(item -> Objects.equals(item.referenceId(), id)).findFirst().orElse(null);
    }

    private boolean sameNumber(BigDecimal expected, BigDecimal actual) {
        return expected.compareTo(actual) == 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void versionFailure() {
        fail(ReportValidationStage.VERSION, "DATASET_VERSION_MISMATCH", "报告引用的数据版本不一致");
    }

    private void fail(ReportValidationStage stage, String code, String message) {
        throw new FinalReportValidationException(stage, code, message);
    }
}
