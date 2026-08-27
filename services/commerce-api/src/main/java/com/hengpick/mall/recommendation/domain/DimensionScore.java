package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.List;

public record DimensionScore(
        /* 五个顶层维度之一。 */
        Dimension dimension,
        /* 尚未叠加用户顶层权重的 0—100 原始维度分。 */
        BigDecimal score,
        /* 已知指标权重占该维度原公式总权重的比例。 */
        BigDecimal completeness,
        /* 实际参与计算的事实标识。 */
        List<String> sourceFactIds,
        /* 未参与计算的缺失指标键。 */
        List<String> missingMetricKeys) {

    public DimensionScore {
        sourceFactIds = List.copyOf(sourceFactIds);
        missingMetricKeys = List.copyOf(missingMetricKeys);
    }
}
