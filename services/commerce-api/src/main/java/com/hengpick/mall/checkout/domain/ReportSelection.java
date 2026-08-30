package com.hengpick.mall.checkout.domain;

import java.math.BigDecimal;

/** 用户所属历史报告中的选中方案。 */
public record ReportSelection(String sessionId, int reportVersion, String skuId, String pricePlanId,
        BigDecimal finalPrice) {}
