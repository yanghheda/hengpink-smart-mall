package com.hengpick.mall.catalog.domain;

import java.util.List;
import java.util.Map;

/** 商品详情及其有效 SKU 列表。 */
public record ProductDetail(
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
         * 类目归一化规格，键由类目 Schema 定义。
         */
        Map<String, Object> canonicalSpecs,
        /*
         * 商品卖点列表。
         */
        List<String> sellingPoints,
        /*
         * 商品限制或注意事项列表。
         */
        List<String> limitations,
        /*
         * 保修说明。
         */
        String warrantySummary,
        /*
         * 商品来源数据集版本。
         */
        String datasetVersion,
        /*
         * 是否为模拟数据。
         */
        boolean simulated,
        /*
         * 当前商品下的有效 SKU 列表。
         */
        List<SkuDetail> skus,
        /*
         * 请求指定 skuId 时命中的 SKU；未指定时为空。
         */
        SkuDetail selectedSku) {}
