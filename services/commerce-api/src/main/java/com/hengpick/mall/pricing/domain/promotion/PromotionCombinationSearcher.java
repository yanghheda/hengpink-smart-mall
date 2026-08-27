package com.hengpick.mall.pricing.domain.promotion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** 校验规则关系并以有界深度优先搜索枚举合法优惠组合。 */
public final class PromotionCombinationSearcher {

    private static final int DEFAULT_MAX_COMBINATIONS = 1_000;
    private static final Comparator<CompiledPromotion> APPLICATION_ORDER = Comparator
            .comparingInt((CompiledPromotion promotion) -> stage(promotion.type()))
            .thenComparing(CompiledPromotion::priority, Comparator.reverseOrder())
            .thenComparing(CompiledPromotion::promotionId);

    private final int maxCombinations;

    public PromotionCombinationSearcher() {
        this(DEFAULT_MAX_COMBINATIONS);
    }

    public PromotionCombinationSearcher(int maxCombinations) {
        if (maxCombinations < 1) {
            throw new IllegalArgumentException("组合上限必须大于零");
        }
        this.maxCombinations = maxCombinations;
    }

    public List<PromotionCombination> search(
            List<CompiledPromotion> promotions,
            PromotionApplicationContext baseContext) {
        Objects.requireNonNull(promotions, "优惠规则不能为空");
        Objects.requireNonNull(baseContext, "优惠执行上下文不能为空");
        validateRuleSet(promotions);

        var candidates = promotions.stream()
                .filter(promotion -> promotion.apply(baseContext).applied())
                .sorted(APPLICATION_ORDER)
                .toList();
        var structurallyLegal = new ArrayList<List<CompiledPromotion>>();
        enumerate(candidates, 0, new ArrayList<>(), structurallyLegal);

        var unique = new LinkedHashMap<String, PromotionCombination>();
        for (var selected : structurallyLegal) {
            execute(selected, baseContext).ifPresent(combination ->
                    unique.putIfAbsent(equivalenceKey(combination), combination));
        }
        return List.copyOf(unique.values());
    }

    private void validateRuleSet(List<CompiledPromotion> promotions) {
        var byId = new HashMap<String, CompiledPromotion>();
        for (var promotion : promotions) {
            if (byId.put(promotion.promotionId(), promotion) != null) {
                throw new IllegalArgumentException("优惠标识重复: " + promotion.promotionId());
            }
            if (promotion.exclusiveWithIds().contains(promotion.promotionId())) {
                throw new IllegalArgumentException("优惠不能与自身互斥: " + promotion.promotionId());
            }
        }
        for (var promotion : promotions) {
            for (var exclusiveId : promotion.exclusiveWithIds()) {
                if (!byId.containsKey(exclusiveId)) {
                    throw new IllegalArgumentException(
                            "优惠 " + promotion.promotionId() + " 引用了不存在的互斥优惠: " + exclusiveId);
                }
            }
        }
        for (var leftIndex = 0; leftIndex < promotions.size(); leftIndex++) {
            for (var rightIndex = leftIndex + 1; rightIndex < promotions.size(); rightIndex++) {
                var left = promotions.get(leftIndex);
                var right = promotions.get(rightIndex);
                var leftAllows = left.stackableWithTypes().contains(right.type());
                var rightAllows = right.stackableWithTypes().contains(left.type());
                if (leftAllows != rightAllows) {
                    throw new IllegalArgumentException(
                            "优惠叠加声明必须双向一致: " + left.promotionId() + " 与 " + right.promotionId());
                }
            }
        }
    }

    private void enumerate(
            List<CompiledPromotion> candidates,
            int index,
            List<CompiledPromotion> selected,
            List<List<CompiledPromotion>> results) {
        if (index == candidates.size()) {
            if (!selected.isEmpty()) {
                if (results.size() >= maxCombinations) {
                    throw new IllegalStateException("合法优惠组合超过上限 " + maxCombinations + "，规则数据需要治理");
                }
                results.add(List.copyOf(selected));
            }
            return;
        }

        var candidate = candidates.get(index);
        if (selected.stream().allMatch(existing -> canStack(existing, candidate))) {
            selected.add(candidate);
            enumerate(candidates, index + 1, selected, results);
            selected.removeLast();
        }
        enumerate(candidates, index + 1, selected, results);
    }

    private boolean canStack(CompiledPromotion left, CompiledPromotion right) {
        return !left.exclusiveWithIds().contains(right.promotionId())
                && !right.exclusiveWithIds().contains(left.promotionId())
                && left.stackableWithTypes().contains(right.type())
                && right.stackableWithTypes().contains(left.type());
    }

    private java.util.Optional<PromotionCombination> execute(
            List<CompiledPromotion> selected,
            PromotionApplicationContext baseContext) {
        var steps = new ArrayList<CalculationStep>();
        var currentAmount = baseContext.currentAmount();
        for (var promotion : selected) {
            var currentContext = new PromotionApplicationContext(
                    baseContext.offer(),
                    baseContext.productId(),
                    baseContext.categoryId(),
                    currentAmount,
                    baseContext.memberships(),
                    baseContext.calculationAt());
            var result = promotion.apply(currentContext);
            if (!result.applied()) {
                return java.util.Optional.empty();
            }
            var step = result.step().orElseThrow();
            steps.add(step);
            currentAmount = step.afterAmount();
        }
        return java.util.Optional.of(new PromotionCombination(
                selected.stream().map(CompiledPromotion::promotionId).toList(), steps, currentAmount));
    }

    private String equivalenceKey(PromotionCombination combination) {
        var key = new StringBuilder(combination.finalAmount().currency())
                .append(':')
                .append(combination.finalAmount());
        for (var step : combination.steps()) {
            key.append('|')
                    .append(step.promotionType())
                    .append(':')
                    .append(step.beforeAmount())
                    .append(':')
                    .append(step.discountAmount())
                    .append(':')
                    .append(step.afterAmount());
        }
        return key.toString();
    }

    private static int stage(PromotionType type) {
        return switch (type) {
            case DIRECT_REDUCTION -> 10;
            case FULL_REDUCTION -> 20;
            case PRODUCT_COUPON -> 30;
            case MEMBER_DISCOUNT -> 40;
        };
    }
}
