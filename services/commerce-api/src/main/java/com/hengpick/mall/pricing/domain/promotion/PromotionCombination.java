package com.hengpick.mall.pricing.domain.promotion;

import com.hengpick.mall.pricing.domain.Money;
import java.util.List;
import java.util.Objects;

/** 一组已按固定顺序执行且可逐步复算的合法优惠组合；空组合表示基础报价。 */
public record PromotionCombination(
        List<String> promotionIds,
        List<CalculationStep> steps,
        Money finalAmount) {

    public PromotionCombination {
        promotionIds = List.copyOf(Objects.requireNonNull(promotionIds, "优惠标识不能为空"));
        steps = List.copyOf(Objects.requireNonNull(steps, "计算步骤不能为空"));
        Objects.requireNonNull(finalAmount, "最终金额不能为空");
        if (promotionIds.size() != steps.size()) {
            throw new IllegalArgumentException("优惠标识与计算步骤数量必须一致");
        }
    }
}
