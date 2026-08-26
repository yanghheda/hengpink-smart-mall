package com.hengpick.mall.catalog.infrastructure;

/** 商品查询 SQL 返回的行模型，不直接作为对外 API 响应。 */
public record ProductRow(
        /*
         * 商品唯一标识。
         */
        String productId,
        /*
         * 所属类目唯一标识。
         */
        String categoryId,
        /*
         * 所属类目展示名称。
         */
        String categoryName,
        /*
         * 商品品牌。
         */
        String brand,
        /*
         * 商品型号。
         */
        String model,
        /*
         * 商品展示名称。
         */
        String displayName,
        /*
         * 商品副标题。
         */
        String subtitle,
        /*
         * 类目归一化规格的 JSON 字符串。
         */
        String canonicalSpecsJson,
        /*
         * 商品卖点列表的 JSON 字符串。
         */
        String sellingPointsJson,
        /*
         * 商品限制列表的 JSON 字符串。
         */
        String limitationJson,
        /*
         * 保修说明。
         */
        String warrantySummary,
        /*
         * 来源数据集版本。
         */
        String datasetVersion,
        /*
         * 是否为模拟数据。
         */
        boolean simulated,
        /*
         * 当前有效 SKU 数量。
         */
        int skuCount) {}
