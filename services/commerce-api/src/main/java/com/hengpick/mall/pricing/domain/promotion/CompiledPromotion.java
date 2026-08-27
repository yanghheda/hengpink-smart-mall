package com.hengpick.mall.pricing.domain.promotion;

import com.hengpick.mall.pricing.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;

/** 已通过编译校验、可直接执行的单条优惠规则。 */
public final class CompiledPromotion {
    private final String promotionId;
    private final PromotionType type;
    private final Set<String> categoryIds;
    private final Set<String> productIds;
    private final Set<String> skuIds;
    private final Money minAmount;
    private final String membership;
    private final Instant startAt;
    private final Instant endAt;
    private final Money amountOff;
    private final BigDecimal discountRate;

    CompiledPromotion(
            String promotionId,
            PromotionType type,
            Set<String> categoryIds,
            Set<String> productIds,
            Set<String> skuIds,
            Money minAmount,
            String membership,
            Instant startAt,
            Instant endAt,
            Money amountOff,
            BigDecimal discountRate) {
        this.promotionId = promotionId;
        this.type = type;
        this.categoryIds = Set.copyOf(categoryIds);
        this.productIds = Set.copyOf(productIds);
        this.skuIds = Set.copyOf(skuIds);
        this.minAmount = minAmount;
        this.membership = membership;
        this.startAt = startAt;
        this.endAt = endAt;
        this.amountOff = amountOff;
        this.discountRate = discountRate;
    }

    public PromotionApplicationResult apply(PromotionApplicationContext context) {
        var rejection = rejectionReason(context);
        if (rejection != null) {
            return PromotionApplicationResult.rejected(rejection);
        }

        var before = context.currentAmount();
        var discount = calculateDiscount(before);
        var after = new Money(before.amount().subtract(discount.amount()), before.currency());
        return PromotionApplicationResult.applied(
                new CalculationStep(promotionId, type, before, discount, after));
    }

    private PromotionRejectionReason rejectionReason(PromotionApplicationContext context) {
        if (!matches(categoryIds, context.categoryId())
                || !matches(productIds, context.productId())
                || !matches(skuIds, context.offer().skuId())) {
            return PromotionRejectionReason.SCOPE_NOT_MATCHED;
        }
        if (startAt != null && context.calculationAt().isBefore(startAt)) {
            return PromotionRejectionReason.NOT_STARTED;
        }
        if (endAt != null && !context.calculationAt().isBefore(endAt)) {
            return PromotionRejectionReason.EXPIRED;
        }
        if (minAmount != null && context.currentAmount().amount().compareTo(minAmount.amount()) < 0) {
            return PromotionRejectionReason.THRESHOLD_NOT_MET;
        }
        if (membership != null && !context.memberships().contains(membership)) {
            return PromotionRejectionReason.MEMBERSHIP_REQUIRED;
        }
        return null;
    }

    private Money calculateDiscount(Money before) {
        BigDecimal rawDiscount;
        if (amountOff != null) {
            rawDiscount = amountOff.amount();
        } else {
            rawDiscount = before.amount().subtract(before.amount().multiply(discountRate));
        }
        var capped = rawDiscount.min(before.amount()).setScale(2, RoundingMode.HALF_UP);
        return new Money(capped, before.currency());
    }

    private boolean matches(Set<String> scope, String actual) {
        return scope.isEmpty() || scope.contains(actual);
    }
}
