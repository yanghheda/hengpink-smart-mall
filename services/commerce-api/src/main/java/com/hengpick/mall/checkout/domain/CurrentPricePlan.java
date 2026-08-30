package com.hengpick.mall.checkout.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** 服务端在创建时重新确认的当前价格事实。 */
public record CurrentPricePlan(String pricePlanId, String skuId, String offerId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal finalPrice,
        String currency, String datasetVersion, long offerVersion) {}
