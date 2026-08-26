package com.hengpick.mall.catalog.domain;

import java.math.BigDecimal;
import java.util.Map;

public record CatalogSearchCandidate(String productId, String skuId, String displayName, String categoryId,
                                     BigDecimal price, String stockStatus, int stockQuantity,
                                     Map<String, Object> attributes) {}
