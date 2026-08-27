package com.hengpick.mall.pricing.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 以最小业务精度表达金额的不可变值对象。 */
public record Money(
        /* 统一保留两位小数的十进制金额。 */
        BigDecimal amount,
        /* ISO 4217 币种代码。 */
        String currency) {

    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("币种必须是三个大写字母");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
    }

    public static Money cny(String amount) {
        return new Money(new BigDecimal(amount), "CNY");
    }

    @Override
    @JsonValue
    public String toString() {
        return amount.toPlainString();
    }
}
