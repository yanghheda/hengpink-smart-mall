package com.hengpick.mall.pricing.domain.promotion;

import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 执行一条优惠所需的确定性事实快照。 */
public record PromotionApplicationContext(
        Offer offer,
        String productId,
        String categoryId,
        Money currentAmount,
        Set<String> memberships,
        Instant calculationAt) {

    public PromotionApplicationContext {
        Objects.requireNonNull(offer, "报价不能为空");
        Objects.requireNonNull(productId, "商品标识不能为空");
        Objects.requireNonNull(categoryId, "类目标识不能为空");
        Objects.requireNonNull(currentAmount, "规则执行前金额不能为空");
        memberships = Set.copyOf(Objects.requireNonNull(memberships, "会员资格集合不能为空"));
        Objects.requireNonNull(calculationAt, "计算时刻不能为空");
        if (!offer.currency().equals(currentAmount.currency())) {
            throw new IllegalArgumentException("报价与规则执行金额的币种必须一致");
        }
    }
}
