package com.hengpick.mall.catalog.domain;

import java.util.List;

public record CatalogSearchResult(List<CatalogSearchCandidate> matched, List<RejectedCandidate> rejected) {
    public record RejectedCandidate(CatalogSearchCandidate candidate, List<String> reasonCodes) {}
}
