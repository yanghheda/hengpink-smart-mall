package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record RiskItem(String code, String causeId, BigDecimal penalty) {

    public RiskItem {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("风险代码不能为空");
        if (causeId == null || causeId.isBlank()) throw new IllegalArgumentException("风险原因标识不能为空");
        Objects.requireNonNull(penalty, "风险扣分不能为空");
        if (penalty.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("风险扣分不能为负数");
    }

    public RiskItem(String code, String causeId, String penalty) {
        this(code, causeId, new BigDecimal(Objects.requireNonNull(penalty)));
    }
}
