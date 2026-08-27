package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record WeightedMetric(
        /* 参与公式的归一化事实。 */
        MetricFact metric,
        /* 指标在原始公式中的非负权重。 */
        BigDecimal weight) {

    public WeightedMetric {
        Objects.requireNonNull(metric, "指标不能为空");
        Objects.requireNonNull(weight, "指标权重不能为空");
        if (weight.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("指标权重不能为负数");
    }

    public WeightedMetric(MetricFact metric, String weight) {
        this(metric, new BigDecimal(Objects.requireNonNull(weight)));
    }
}
