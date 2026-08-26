package com.hengpick.mall.catalog.infrastructure;

/** SKU 查询 SQL 返回的行模型，不直接作为对外 API 响应。 */
public record SkuRow(
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
         * SKU 差异属性的 JSON 字符串。
         */
        String attributesJson,
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
