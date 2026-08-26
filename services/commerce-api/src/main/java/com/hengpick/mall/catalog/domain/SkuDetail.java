package com.hengpick.mall.catalog.domain;

import java.util.Map;

/** 商品可售变体（SKU）的详情。 */
public record SkuDetail(
        /*
         * SKU 唯一标识。
         */
        String skuId,
        /*
         * SKU 业务编码。
         */
        String skuCode,
        /*
         * SKU 展示名称。
         */
        String displayName,
        /*
         * SKU 差异属性，键由类目 Schema 定义。
         */
        Map<String, Object> attributes,
        /*
         * 库存状态代码。
         */
        String stockStatus,
        /*
         * 可用库存数量。
         */
        int stockQuantity,
        /*
         * 保修月数。
         */
        int warrantyMonths) {}
