package com.hengpick.mall.catalog.domain;

import java.util.Optional;

public interface CatalogQueryPort {
    ProductPage findProducts(int page, int size);

    Optional<ProductDetail> findProduct(String productId, String skuId);
}
