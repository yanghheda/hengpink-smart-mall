package com.hengpick.mall.pricing.infrastructure;

import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.domain.OfferQueryPort;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 将 MyBatis 报价行转换为 Pricing 领域对象。 */
@Component
@Profile("database")
public final class MyBatisOfferQueryAdapter implements OfferQueryPort {
    private final OfferMapper mapper;

    public MyBatisOfferQueryAdapter(OfferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Offer> findValidOffers(String skuId, Instant calculationAt) {
        var calculationAtUtc = LocalDateTime.ofInstant(calculationAt, ZoneOffset.UTC);
        return mapper.findValidOffers(skuId, calculationAtUtc).stream()
                .map(row -> new Offer(
                        row.offerId(),
                        row.skuId(),
                        row.shopId(),
                        new Money(row.listPrice(), row.currency()),
                        new Money(row.salePrice(), row.currency()),
                        new Money(row.additionalFee(), row.currency()),
                        row.validFrom().toInstant(ZoneOffset.UTC),
                        row.validTo().toInstant(ZoneOffset.UTC),
                        row.datasetVersion(),
                        row.version()))
                .toList();
    }
}
