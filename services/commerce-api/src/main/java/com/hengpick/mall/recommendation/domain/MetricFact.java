package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MetricFact(
        /* 公式中的稳定指标键。 */
        String key,
        /* 已归一化到 0—100 的指标值；缺失时为 null。 */
        BigDecimal normalizedScore,
        /* 可回溯到结构化事实或证据的标识。 */
        String factId) {

    public MetricFact {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("指标键不能为空");
        if (factId == null || factId.isBlank()) throw new IllegalArgumentException("事实标识不能为空");
        if (normalizedScore != null
                && (normalizedScore.compareTo(BigDecimal.ZERO) < 0
                || normalizedScore.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("归一化指标必须在 0 到 100 之间");
        }
    }

    public static MetricFact known(String key, String normalizedScore, String factId) {
        return new MetricFact(key, new BigDecimal(Objects.requireNonNull(normalizedScore)), factId);
    }

    public static MetricFact missing(String key, String factId) {
        return new MetricFact(key, null, factId);
    }

    public boolean known() {
        return normalizedScore != null;
    }
}
