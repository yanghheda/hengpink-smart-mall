package com.hengpick.mall.catalog.domain;

import java.util.List;

/** 商品列表的分页查询结果。 */
public record ProductPage(
        /*
         * 当前页的商品列表。
         */
        List<ProductSummary> items,
        /*
         * 从零开始的页码。
         */
        int page,
        /*
         * 每页条数，范围为 1 至 100。
         */
        int size,
        /*
         * 符合条件的商品总数。
         */
        long totalElements) {
    public int totalPages() {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
