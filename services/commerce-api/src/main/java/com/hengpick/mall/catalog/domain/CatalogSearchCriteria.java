package com.hengpick.mall.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

public record CatalogSearchCriteria(String categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                    boolean inStockOnly, List<AttributeConstraint> attributes) {
    public CatalogSearchCriteria {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
