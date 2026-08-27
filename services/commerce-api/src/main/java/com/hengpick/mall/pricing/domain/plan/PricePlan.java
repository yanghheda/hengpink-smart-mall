package com.hengpick.mall.pricing.domain.plan;

import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.promotion.CalculationStep;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 一次确定性计算形成的、可供报告引用的价格方案快照。 */
public record PricePlan(
        /* 当前快照内稳定的价格方案标识。 */
        String pricePlanId,
        /* 价格方案所服务的购买目标。 */
        PricePlanType type,
        /* 方案绑定的报价标识。 */
        String offerId,
        /* 方案绑定的 SKU 标识。 */
        String skuId,
        /* 参与计算的报价版本。 */
        long offerVersion,
        /* 参与计算的数据集版本。 */
        String datasetVersion,
        /* 参与计算的优惠规则版本。 */
        String pricingRuleVersion,
        /* 固定的计算时刻。 */
        Instant calculationAt,
        /* 计算时冻结的用户会员资格。 */
        Set<String> memberships,
        /* 报价的商品标价。 */
        Money listPrice,
        /* 优惠计算的起始销售价。 */
        Money salePrice,
        /* 购买必须支付的附加费用。 */
        Money additionalFee,
        /* 包含附加费用的预计到手价。 */
        Money finalPrice,
        /* 按业务顺序应用的优惠标识。 */
        List<String> appliedPromotionIds,
        /* 可逐项复算的优惠金额步骤。 */
        List<CalculationStep> steps,
        /* 使用该方案额外要求的资格。 */
        Set<String> requirements) {

    public PricePlan {
        Objects.requireNonNull(pricePlanId, "价格方案标识不能为空");
        Objects.requireNonNull(type, "价格方案类型不能为空");
        Objects.requireNonNull(offerId, "报价标识不能为空");
        Objects.requireNonNull(skuId, "SKU 标识不能为空");
        Objects.requireNonNull(datasetVersion, "数据集版本不能为空");
        Objects.requireNonNull(pricingRuleVersion, "优惠规则版本不能为空");
        Objects.requireNonNull(calculationAt, "计算时刻不能为空");
        memberships = Set.copyOf(Objects.requireNonNull(memberships, "会员资格快照不能为空"));
        Objects.requireNonNull(listPrice, "标价不能为空");
        Objects.requireNonNull(salePrice, "销售价不能为空");
        Objects.requireNonNull(additionalFee, "附加费用不能为空");
        Objects.requireNonNull(finalPrice, "最终价格不能为空");
        appliedPromotionIds = List.copyOf(Objects.requireNonNull(appliedPromotionIds, "优惠标识不能为空"));
        steps = List.copyOf(Objects.requireNonNull(steps, "计算步骤不能为空"));
        requirements = Set.copyOf(Objects.requireNonNull(requirements, "方案资格不能为空"));
        if (appliedPromotionIds.size() != steps.size()) {
            throw new IllegalArgumentException("优惠标识与计算步骤数量必须一致");
        }
        validateCurrencies(listPrice, salePrice, additionalFee, finalPrice);
        validateReplay(salePrice, additionalFee, finalPrice, steps);
    }

    private static void validateCurrencies(Money... amounts) {
        var currency = amounts[0].currency();
        for (var amount : amounts) {
            if (!currency.equals(amount.currency())) {
                throw new IllegalArgumentException("价格方案中的金额币种必须一致");
            }
        }
    }

    private static void validateReplay(
            Money salePrice,
            Money additionalFee,
            Money finalPrice,
            List<CalculationStep> steps) {
        var current = salePrice;
        for (var step : steps) {
            if (!current.equals(step.beforeAmount())) {
                throw new IllegalArgumentException("价格方案的优惠步骤金额不连续");
            }
            current = step.afterAmount();
        }
        var replayed = current.amount().add(additionalFee.amount());
        if (replayed.compareTo(finalPrice.amount()) != 0) {
            throw new IllegalArgumentException("价格方案最终金额无法由优惠步骤和附加费用复算");
        }
    }
}
