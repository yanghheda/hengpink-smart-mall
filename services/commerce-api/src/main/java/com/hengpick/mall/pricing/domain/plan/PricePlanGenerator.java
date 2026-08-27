package com.hengpick.mall.pricing.domain.plan;

import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.promotion.CompiledPromotion;
import com.hengpick.mall.pricing.domain.promotion.PromotionApplicationContext;
import com.hengpick.mall.pricing.domain.promotion.PromotionCombination;
import com.hengpick.mall.pricing.domain.promotion.PromotionCombinationSearcher;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 从合法优惠组合中确定性选择三类用户可见价格方案。 */
public final class PricePlanGenerator {

    private static final Comparator<CandidatePlan> LOWEST_PRICE_ORDER = Comparator
            .comparing((CandidatePlan candidate) -> candidate.finalPrice().amount())
            .thenComparingInt(candidate -> candidate.combination().steps().size())
            .thenComparing(CandidatePlan::stableKey);
    private static final Comparator<CandidatePlan> MIN_STEPS_ORDER = Comparator
            .comparingInt((CandidatePlan candidate) -> candidate.combination().steps().size())
            .thenComparing(candidate -> candidate.finalPrice().amount())
            .thenComparing(CandidatePlan::stableKey);

    private final PromotionCombinationSearcher combinationSearcher;

    public PricePlanGenerator(PromotionCombinationSearcher combinationSearcher) {
        this.combinationSearcher = Objects.requireNonNull(combinationSearcher, "优惠组合搜索器不能为空");
    }

    public List<PricePlan> generate(
            PromotionApplicationContext context,
            List<CompiledPromotion> promotions,
            String pricingRuleVersion) {
        Objects.requireNonNull(context, "价格计算上下文不能为空");
        Objects.requireNonNull(promotions, "优惠规则不能为空");
        Objects.requireNonNull(pricingRuleVersion, "优惠规则版本不能为空");
        if (pricingRuleVersion.isBlank()) {
            throw new IllegalArgumentException("优惠规则版本不能为空白");
        }
        if (!context.offer().isValidAt(context.calculationAt())) {
            throw new IllegalArgumentException("报价在固定计算时刻无效");
        }
        if (!context.currentAmount().equals(context.offer().salePrice())) {
            throw new IllegalArgumentException("价格方案必须从报价销售价开始计算");
        }

        var promotionById = new HashMap<String, CompiledPromotion>();
        promotions.forEach(promotion -> promotionById.put(promotion.promotionId(), promotion));
        var candidates = combinationSearcher.search(promotions, context).stream()
                .map(combination -> candidate(combination, context, promotionById))
                .toList();

        var lowest = candidates.stream().min(LOWEST_PRICE_ORDER).orElseGet(() -> baseCandidate(context));
        var noMembership = candidates.stream()
                .filter(candidate -> candidate.requirements().isEmpty())
                .min(LOWEST_PRICE_ORDER)
                .orElseGet(() -> baseCandidate(context));
        var minSteps = candidates.stream().min(MIN_STEPS_ORDER).orElseGet(() -> baseCandidate(context));

        return List.of(
                snapshot(PricePlanType.LOWEST_PRICE, lowest, context, pricingRuleVersion),
                snapshot(PricePlanType.NO_MEMBERSHIP, noMembership, context, pricingRuleVersion),
                snapshot(PricePlanType.MIN_STEPS, minSteps, context, pricingRuleVersion));
    }

    private CandidatePlan candidate(
            PromotionCombination combination,
            PromotionApplicationContext context,
            Map<String, CompiledPromotion> promotionById) {
        var requirements = new TreeSet<String>();
        for (var promotionId : combination.promotionIds()) {
            var promotion = promotionById.get(promotionId);
            if (promotion == null) {
                throw new IllegalStateException("优惠组合引用了不存在的规则: " + promotionId);
            }
            promotion.membershipRequirement().ifPresent(requirements::add);
        }
        return new CandidatePlan(
                combination,
                addFee(combination.finalAmount(), context.offer().additionalFee()),
                Set.copyOf(requirements));
    }

    private CandidatePlan baseCandidate(PromotionApplicationContext context) {
        var combination = new PromotionCombination(List.of(), List.of(), context.currentAmount());
        return new CandidatePlan(
                combination,
                addFee(context.currentAmount(), context.offer().additionalFee()),
                Set.of());
    }

    private PricePlan snapshot(
            PricePlanType type,
            CandidatePlan candidate,
            PromotionApplicationContext context,
            String pricingRuleVersion) {
        var offer = context.offer();
        var planId = String.join(":",
                offer.offerId(),
                type.name(),
                pricingRuleVersion,
                candidate.stableKey().isEmpty() ? "BASE" : candidate.stableKey());
        return new PricePlan(
                planId,
                type,
                offer.offerId(),
                offer.skuId(),
                offer.version(),
                offer.datasetVersion(),
                pricingRuleVersion,
                context.calculationAt(),
                context.memberships(),
                offer.listPrice(),
                offer.salePrice(),
                offer.additionalFee(),
                candidate.finalPrice(),
                candidate.combination().promotionIds(),
                candidate.combination().steps(),
                candidate.requirements());
    }

    private Money addFee(Money amount, Money fee) {
        if (!amount.currency().equals(fee.currency())) {
            throw new IllegalArgumentException("优惠后金额与附加费用币种必须一致");
        }
        return new Money(amount.amount().add(fee.amount()), amount.currency());
    }

    private record CandidatePlan(
            PromotionCombination combination,
            Money finalPrice,
            Set<String> requirements) {

        private String stableKey() {
            return String.join(",", combination.promotionIds());
        }
    }
}
