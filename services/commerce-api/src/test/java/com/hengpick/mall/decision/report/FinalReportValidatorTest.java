package com.hengpick.mall.decision.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalReportValidatorTest {
    private final FinalReportValidator validator = new FinalReportValidator();

    @Test
    void acceptsReportOnlyWhenEveryProjectedFactMatchesJavaSnapshot() {
        var result = validator.validate(validDraft(), validContext());

        assertThat(result).isEqualTo(validDraft());
    }

    @Test
    void rejectsNonexistentEvidence() {
        var draft = draftWith(reason(List.of("FACT-1"), List.of("EV-NOT-FOUND")), "SKU-1", "PLAN-1", "2999.00", "92.00");

        assertViolation(draft, ReportValidationStage.EVIDENCE, "EVIDENCE_NOT_FOUND");
    }

    @Test
    void rejectsChangedScoreForFirstRankedCandidate() {
        var draft = draftWith(reason(List.of("FACT-1"), List.of("EV-1")), "SKU-1", "PLAN-1", "2999.00", "99.99");

        assertViolation(draft, ReportValidationStage.RANKING, "SCORE_MISMATCH");
    }

    @Test
    void rejectsPricePlanOwnedByAnotherSku() {
        var draft = draftWith(reason(List.of("FACT-1"), List.of("EV-1")), "SKU-1", "PLAN-2", "2599.00", "92.00");

        assertViolation(draft, ReportValidationStage.AMOUNT, "PRICE_PLAN_SKU_MISMATCH");
    }

    @Test
    void reportsIdFailureBeforeLaterAmountAndEvidenceFailures() {
        var draft = draftWith(reason(List.of(), List.of("EV-NOT-FOUND")), "SKU-NOT-FOUND", "PLAN-2", "0.01", "100.00");

        assertViolation(draft, ReportValidationStage.ID, "CANDIDATE_NOT_FOUND");
    }

    @Test
    void rejectsHardConstraintBeforeRankingMismatch() {
        var draft = draftWith(reason(List.of("FACT-2"), List.of("EV-2")), "SKU-2", "PLAN-2", "2599.00", "100.00");

        assertViolation(draft, ReportValidationStage.HARD_CONSTRAINT, "HARD_CONSTRAINT_VIOLATED");
    }

    @Test
    void rejectsCrossVersionFactAtFinalVersionGate() {
        var draft = draftWith(reason(List.of("FACT-OLD"), List.of("EV-1")), "SKU-1", "PLAN-1", "2999.00", "92.00");

        assertViolation(draft, ReportValidationStage.VERSION, "DATASET_VERSION_MISMATCH");
    }

    @Test
    void rejectsReferenceOwnedByAnotherSku() {
        var draft = draftWith(reason(List.of("FACT-2"), List.of("EV-1")), "SKU-1", "PLAN-1", "2999.00", "92.00");

        assertViolation(draft, ReportValidationStage.EVIDENCE, "FACT_OWNERSHIP_MISMATCH");
    }

    private void assertViolation(FinalReportDraft draft, ReportValidationStage stage, String code) {
        assertThatThrownBy(() -> validator.validate(draft, validContext()))
                .isInstanceOf(FinalReportValidationException.class)
                .satisfies(error -> {
                    var violation = (FinalReportValidationException) error;
                    assertThat(violation.stage()).isEqualTo(stage);
                    assertThat(violation.code()).isEqualTo(code);
                });
    }

    private FinalReportDraft validDraft() {
        return draftWith(reason(List.of("FACT-1"), List.of("EV-1")), "SKU-1", "PLAN-1", "2999.00", "92.00");
    }

    private FinalReportDraft draftWith(
            FinalReportDraft.Reason reason,
            String skuId,
            String pricePlanId,
            String finalPrice,
            String finalScore) {
        return new FinalReportDraft(
                "DATASET-V1",
                "首选更符合当前需求。",
                List.of(new FinalReportDraft.Recommendation(
                        1, "PRODUCT-1", skuId, new BigDecimal(finalScore), pricePlanId,
                        new BigDecimal(finalPrice), true, List.of(reason))),
                List.of());
    }

    private FinalReportDraft.Reason reason(List<String> factIds, List<String> evidenceIds) {
        return new FinalReportDraft.Reason("续航表现适合日常使用。", factIds, evidenceIds);
    }

    private ReportValidationContext validContext() {
        return new ReportValidationContext(
                "DATASET-V1",
                List.of(
                        new ReportValidationContext.Candidate("PRODUCT-1", "SKU-1", 1,
                                new BigDecimal("92.00"), true, true, "DATASET-V1"),
                        new ReportValidationContext.Candidate("PRODUCT-1", "SKU-2", 2,
                                new BigDecimal("88.00"), false, true, "DATASET-V1")),
                List.of(
                        new ReportValidationContext.Price("PLAN-1", "SKU-1", new BigDecimal("2999.00"), "DATASET-V1"),
                        new ReportValidationContext.Price("PLAN-2", "SKU-2", new BigDecimal("2599.00"), "DATASET-V1")),
                List.of(
                        new ReportValidationContext.Reference("FACT-1", "PRODUCT-1", "SKU-1", true, "DATASET-V1"),
                        new ReportValidationContext.Reference("FACT-2", "PRODUCT-1", "SKU-2", true, "DATASET-V1"),
                        new ReportValidationContext.Reference("FACT-OLD", "PRODUCT-1", "SKU-1", true, "DATASET-V0")),
                List.of(
                        new ReportValidationContext.Reference("EV-1", "PRODUCT-1", "SKU-1", true, "DATASET-V1"),
                        new ReportValidationContext.Reference("EV-2", "PRODUCT-1", "SKU-2", true, "DATASET-V1")));
    }
}
