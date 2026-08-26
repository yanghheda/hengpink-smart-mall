package com.hengpick.mall.catalog.infrastructure;

import java.math.BigDecimal;

public record SearchCandidateRow(String productId, String skuId, String displayName, String categoryId,
                                 BigDecimal price, String stockStatus, int stockQuantity,
                                 String canonicalSpecsJson, String attributesJson) {}
