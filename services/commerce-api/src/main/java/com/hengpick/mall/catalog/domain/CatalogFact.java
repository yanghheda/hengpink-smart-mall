package com.hengpick.mall.catalog.domain;

/** 可被报告引用的确定性商品事实。 */
public record CatalogFact(String factId, String scope, String productId, String skuId,
                          String attribute, Object value, String datasetVersion) {}
