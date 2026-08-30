package com.hengpick.mall.catalog.infrastructure;

public record ComparisonCandidateRow(
        String productId,
        String skuId,
        String displayName,
        String categoryId,
        String canonicalSpecsJson,
        String attributesJson) {}
