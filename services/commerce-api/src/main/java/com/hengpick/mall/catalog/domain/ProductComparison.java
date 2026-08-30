package com.hengpick.mall.catalog.domain;

import java.util.List;

public record ProductComparison(
        String categoryId,
        String schemaVersion,
        ComparisonMode mode,
        List<Product> products,
        List<Row> rows) {
    public record Product(String productId, String skuId, String displayName) {}

    public record Row(String attributeKey, String label, String unit, List<Object> values) {}
}
