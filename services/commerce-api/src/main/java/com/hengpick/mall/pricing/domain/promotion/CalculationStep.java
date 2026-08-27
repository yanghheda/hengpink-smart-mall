package com.hengpick.mall.pricing.domain.promotion;

import com.hengpick.mall.pricing.domain.Money;

/** 一条优惠成功执行后的可复算金额步骤。 */
public record CalculationStep(
        String promotionId,
        PromotionType promotionType,
        Money beforeAmount,
        Money discountAmount,
        Money afterAmount) {}
