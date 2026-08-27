package com.hengpick.mall.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.web.PricingController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PricingControllerTest {

    @Test
    void returnsMoneyAsTwoDecimalStringsAndExposesTheFixedCalculationTime() throws Exception {
        var calculationAt = Instant.parse("2026-08-27T02:00:00Z");
        var offer = new Offer(
                "O-1", "SKU-1", "SHOP-1",
                Money.cny("3099"), Money.cny("2999"), Money.cny("12.5"),
                Instant.parse("2026-08-27T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"),
                "commerce-demo-2026.08.1", 3);
        var service = new OfferQueryService((skuId, instant) -> List.of(offer),
                Clock.fixed(calculationAt, ZoneOffset.UTC));
        var mockMvc = MockMvcBuilders.standaloneSetup(new PricingController(service)).build();

        mockMvc.perform(get("/api/v1/skus/SKU-1/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calculationAt").value("2026-08-27T02:00:00Z"))
                .andExpect(jsonPath("$.data.offers[0].listPrice").value("3099.00"))
                .andExpect(jsonPath("$.data.offers[0].salePrice").value("2999.00"))
                .andExpect(jsonPath("$.data.offers[0].additionalFee").value("12.50"))
                .andExpect(jsonPath("$.data.offers[0].currency").value("CNY"));
    }
}
