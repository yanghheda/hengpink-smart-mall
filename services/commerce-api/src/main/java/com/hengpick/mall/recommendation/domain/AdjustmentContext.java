package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record AdjustmentContext(
        BigDecimal budgetLimit,
        Set<String> memberships,
        Map<Dimension, BigDecimal> weights) {

    public AdjustmentContext {
        Objects.requireNonNull(budgetLimit, "预算上限不能为空");
        if (budgetLimit.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("预算上限不能为负数");
        memberships = Set.copyOf(memberships);
        weights = Map.copyOf(new EnumMap<>(weights));
    }

    public AdjustmentContext withBudgetLimit(BigDecimal value) {
        return new AdjustmentContext(value, memberships, weights);
    }

    public AdjustmentContext withMemberships(Set<String> value) {
        return new AdjustmentContext(budgetLimit, value, weights);
    }

    public AdjustmentContext withWeights(Map<Dimension, BigDecimal> value) {
        return new AdjustmentContext(budgetLimit, memberships, value);
    }
}
