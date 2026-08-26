package com.hengpick.mall.catalog.application;

public final class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("商品不存在，或指定 SKU 不属于该商品");
    }
}
