package com.hengpick.mall.catalog.domain;

@FunctionalInterface
public interface CategorySearchSchemaPort {
    CategorySearchSchema findSearchSchema(String categoryId);
}
