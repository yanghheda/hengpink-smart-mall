package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.List;

public record CapabilityScore(
        /* 能力稳定键，例如 battery。 */
        String capability,
        /* 缺失项重归一后得到的 0—100 能力分。 */
        BigDecimal score,
        /* 已知指标权重占原公式总权重的比例。 */
        BigDecimal completeness,
        /* 生成该能力分的公式版本。 */
        String formulaVersion,
        /* 实际参与计算的事实标识。 */
        List<String> sourceFactIds,
        /* 未参与计算的缺失指标键。 */
        List<String> missingMetricKeys) {

    public CapabilityScore {
        sourceFactIds = List.copyOf(sourceFactIds);
        missingMetricKeys = List.copyOf(missingMetricKeys);
    }
}
