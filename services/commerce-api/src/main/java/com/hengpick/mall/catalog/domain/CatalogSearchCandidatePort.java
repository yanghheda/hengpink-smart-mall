package com.hengpick.mall.catalog.domain;

import java.util.List;

@FunctionalInterface
public interface CatalogSearchCandidatePort {
    List<CatalogSearchCandidate> findByCategory(String categoryId);
}
