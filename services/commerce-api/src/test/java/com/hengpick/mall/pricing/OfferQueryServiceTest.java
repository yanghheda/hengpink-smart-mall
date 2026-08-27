package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferQueryServiceTest {

    private static final Instant CALCULATION_AT = Instant.parse("2026-08-27T02:00:00Z");

    @Test
    void usesOneFixedClockInstantAndKeepsTheHalfOpenValidityBoundary() {
        var active = offer("O-ACTIVE", "2026-08-27T01:00:00Z", "2026-08-27T03:00:00Z");
        var endingNow = offer("O-END", "2026-08-27T01:00:00Z", "2026-08-27T02:00:00Z");
        var future = offer("O-FUTURE", "2026-08-27T02:00:00Z", "2026-08-27T04:00:00Z");
        var service = new OfferQueryService(
                (skuId, calculationAt) -> List.of(active, endingNow, future),
                Clock.fixed(CALCULATION_AT, ZoneOffset.UTC));

        var result = service.findValidOffers("SKU-1");

        assertThat(result.calculationAt()).isEqualTo(CALCULATION_AT);
        assertThat(result.offers()).extracting(Offer::offerId).containsExactly("O-ACTIVE", "O-FUTURE");
    }

    private Offer offer(String offerId, String validFrom, String validTo) {
        return new Offer(
                offerId,
                "SKU-1",
                "SHOP-1",
                Money.cny("3099.00"),
                Money.cny("2999.00"),
                Money.cny("0.00"),
                Instant.parse(validFrom),
                Instant.parse(validTo),
                "commerce-demo-2026.08.1",
                0);
    }
}
