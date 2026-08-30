package com.hengpick.mall.checkout.domain;

import java.time.Instant;

/** 不可支付、不可履约的购买意向快照。 */
public record PurchaseIntent(String id, String userId, String sessionId, int reportVersion, String skuId,
        CurrentPricePlan pricePlanSnapshot, PurchaseIntentStatus status, Instant expiresAt, Instant createdAt,
        Instant confirmedAt, String idempotencyKey) {
    public PurchaseIntent confirm(Instant now) {
        if (status == PurchaseIntentStatus.CONFIRMED) return this;
        if (status != PurchaseIntentStatus.CREATED || !now.isBefore(expiresAt)) {
            throw new IllegalStateException("购买意向已失效，不能确认");
        }
        return new PurchaseIntent(id, userId, sessionId, reportVersion, skuId, pricePlanSnapshot,
                PurchaseIntentStatus.CONFIRMED, expiresAt, createdAt, now, idempotencyKey);
    }
}
