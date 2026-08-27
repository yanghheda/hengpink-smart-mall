package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record ConfidenceInput(
        BigDecimal dataCompleteness,
        BigDecimal evidenceCoverage,
        BigDecimal skuMatchCertainty,
        BigDecimal pricingCertainty,
        BigDecimal conflictPenalty,
        boolean criticalAttributeMissing) {

    public ConfidenceInput {
        validateUnit(dataCompleteness, "数据完整度");
        validateUnit(evidenceCoverage, "证据覆盖率");
        validateUnit(skuMatchCertainty, "SKU 匹配确定性");
        validateUnit(pricingCertainty, "价格确定性");
        validateUnit(conflictPenalty, "冲突扣减");
    }

    public ConfidenceInput(
            String dataCompleteness,
            String evidenceCoverage,
            String skuMatchCertainty,
            String pricingCertainty,
            String conflictPenalty,
            boolean criticalAttributeMissing) {
        this(decimal(dataCompleteness), decimal(evidenceCoverage), decimal(skuMatchCertainty),
                decimal(pricingCertainty), decimal(conflictPenalty), criticalAttributeMissing);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(Objects.requireNonNull(value));
    }

    private static void validateUnit(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + "必须在 0 到 1 之间");
        }
    }
}
