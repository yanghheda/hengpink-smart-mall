package com.hengpick.mall.catalog.domain;

import java.util.List;

public interface ProductComparisonPort {
    List<ComparisonCandidate> findCandidates(List<String> skuIds);

    CategoryComparisonSchema findSchema(String categoryId);
}
