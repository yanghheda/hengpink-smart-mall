package com.hengpick.mall.catalog.domain;

import java.util.Map;

public record ComparisonCandidate(
        String productId,
        String skuId,
        String displayName,
        String categoryId,
        Map<String, Object> attributes) {}
