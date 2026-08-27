package com.hengpick.mall.pricing.application;

import com.hengpick.mall.pricing.domain.Offer;
import java.time.Instant;
import java.util.List;

/** 一次固定计算时刻下的有效报价查询结果。 */
public record OfferQueryResult(
        /* 被查询的 SKU 标识。 */
        String skuId,
        /* 本次查询唯一使用的服务端计算时刻。 */
        Instant calculationAt,
        /* 在该计算时刻有效的报价。 */
        List<Offer> offers) {}
