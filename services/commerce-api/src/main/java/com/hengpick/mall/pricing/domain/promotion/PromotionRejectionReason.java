package com.hengpick.mall.pricing.domain.promotion;

/** 单条优惠未应用时的稳定原因码。 */
public enum PromotionRejectionReason {
    SCOPE_NOT_MATCHED,
    NOT_STARTED,
    EXPIRED,
    THRESHOLD_NOT_MET,
    MEMBERSHIP_REQUIRED
}
