package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;

public record ConfidenceScore(
        BigDecimal score,
        ConfidenceLevel level,
        ConfidenceInput input) {
}
