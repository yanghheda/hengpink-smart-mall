package com.hengpick.mall.pricing.application;

import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.domain.OfferQueryPort;
import java.time.Clock;
import java.util.Comparator;
import java.util.Objects;

/** 使用服务端时钟编排有效报价查询。 */
public final class OfferQueryService {
    private final OfferQueryPort queryPort;
    private final Clock clock;

    public OfferQueryService(OfferQueryPort queryPort, Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "报价查询端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public OfferQueryResult findValidOffers(String skuId) {
        if (skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("SKU 标识不能为空");
        }
        var calculationAt = clock.instant();
        var offers = queryPort.findValidOffers(skuId, calculationAt).stream()
                .filter(offer -> offer.skuId().equals(skuId))
                .filter(offer -> offer.isValidAt(calculationAt))
                .sorted(Comparator.comparing((Offer offer) -> offer.salePrice().amount())
                        .thenComparing(offer -> offer.offerId()))
                .toList();
        return new OfferQueryResult(skuId, calculationAt, offers);
    }
}
