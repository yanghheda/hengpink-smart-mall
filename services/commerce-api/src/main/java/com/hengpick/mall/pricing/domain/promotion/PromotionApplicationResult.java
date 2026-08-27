package com.hengpick.mall.pricing.domain.promotion;

import java.util.Optional;

/** 单条优惠的应用结果；成功步骤和拒绝原因互斥。 */
public record PromotionApplicationResult(
        boolean applied,
        Optional<CalculationStep> step,
        Optional<PromotionRejectionReason> rejectionReason) {

    static PromotionApplicationResult applied(CalculationStep step) {
        return new PromotionApplicationResult(true, Optional.of(step), Optional.empty());
    }

    static PromotionApplicationResult rejected(PromotionRejectionReason reason) {
        return new PromotionApplicationResult(false, Optional.empty(), Optional.of(reason));
    }
}
