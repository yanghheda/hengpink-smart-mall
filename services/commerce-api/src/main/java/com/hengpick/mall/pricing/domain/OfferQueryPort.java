package com.hengpick.mall.pricing.domain;

import java.time.Instant;
import java.util.List;

/** Pricing 应用层读取有效报价的领域端口。 */
@FunctionalInterface
public interface OfferQueryPort {
    List<Offer> findValidOffers(String skuId, Instant calculationAt);
}
