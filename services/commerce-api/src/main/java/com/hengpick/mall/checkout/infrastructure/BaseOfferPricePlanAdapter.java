package com.hengpick.mall.checkout.infrastructure;

import com.hengpick.mall.checkout.application.PricePlanStaleException;
import com.hengpick.mall.checkout.domain.CurrentPricePlan;
import com.hengpick.mall.checkout.domain.CurrentPricePlanPort;
import com.hengpick.mall.pricing.application.OfferQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 用当前有效报价重新确认 BASE 价格方案。 */
@Component
@Profile("database")
public final class BaseOfferPricePlanAdapter implements CurrentPricePlanPort {
    private final OfferQueryService offerQueryService;
    public BaseOfferPricePlanAdapter(OfferQueryService offerQueryService) { this.offerQueryService = offerQueryService; }

    @Override public CurrentPricePlan revalidate(String skuId, String pricePlanId) {
        if (!pricePlanId.endsWith(":BASE")) throw new PricePlanStaleException();
        var offerId = pricePlanId.substring(0, pricePlanId.length() - ":BASE".length());
        var offer = offerQueryService.findValidOffers(skuId).offers().stream()
                .filter(item -> item.offerId().equals(offerId)).findFirst()
                .orElseThrow(PricePlanStaleException::new);
        var finalPrice = offer.salePrice().amount().add(offer.additionalFee().amount());
        return new CurrentPricePlan(pricePlanId, skuId, offerId, finalPrice, offer.currency(),
                offer.datasetVersion(), offer.version());
    }
}
