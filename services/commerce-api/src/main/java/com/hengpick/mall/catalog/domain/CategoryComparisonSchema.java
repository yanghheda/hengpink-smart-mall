package com.hengpick.mall.catalog.domain;

import java.util.List;

public record CategoryComparisonSchema(String categoryId, String schemaVersion, List<Attribute> attributes) {
    public record Attribute(String key, String label, String unit, boolean comparable) {}
}
