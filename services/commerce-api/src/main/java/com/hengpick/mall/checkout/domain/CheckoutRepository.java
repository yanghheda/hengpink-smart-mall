package com.hengpick.mall.checkout.domain;

import java.util.Optional;

/** PurchaseIntent 持久化及报告归属查询边界。 */
public interface CheckoutRepository {
    Optional<ReportSelection> findReportSelection(String userId, String sessionId, int reportVersion, String skuId);
    Optional<PurchaseIntent> findByIdempotencyKey(String userId, String key);
    Optional<PurchaseIntent> findOwned(String userId, String id);
    void insert(PurchaseIntent intent);
    void update(PurchaseIntent intent);
}
