package com.hengpick.mall.catalog.domain;

/** 商品列表中展示的轻量商品信息。 */
public record ProductSummary(
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
         * 商品来源数据集版本。
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
