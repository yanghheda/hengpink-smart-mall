package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.pricing.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundsHalfUpAndSerializesAsAStringWithTwoDecimalPlaces() throws Exception {
        var money = Money.cny("10.125");

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.13"));
        assertThat(objectMapper.writeValueAsString(money)).isEqualTo("\"10.13\"");
    }

    @Test
    void buildsDecimalMoneyFromTextWithoutBinaryFloatingPointNoise() {
        var exact = Money.cny("0.10");
        var noisy = new BigDecimal(0.1);

        assertThat(exact.amount()).isEqualTo(new BigDecimal("0.10"));
        assertThat(noisy).isNotEqualTo(new BigDecimal("0.10"));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.cny("-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("金额不能为负数");
    }
}
