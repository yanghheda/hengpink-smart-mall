package com.hengpick.mall.catalog.application;

import com.hengpick.mall.catalog.domain.AttributeConstraint;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidate;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidatePort;
import com.hengpick.mall.catalog.domain.CatalogSearchCriteria;
import com.hengpick.mall.catalog.domain.CatalogSearchResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("database")
public class CatalogSearchService {
    private static final Map<String, Set<String>> PHONE_OPERATORS = Map.of(
            "batteryMah", Set.of(">=", "<="), "ramGb", Set.of(">=", "="),
            "storageGb", Set.of(">=", "="), "weightG", Set.of("<="),
            "ois", Set.of("="), "simpleMode", Set.of("="));
    private final CatalogSearchCandidatePort candidatePort;

    public CatalogSearchService(CatalogSearchCandidatePort candidatePort) {
        this.candidatePort = candidatePort;
    }

    public CatalogSearchResult search(CatalogSearchCriteria criteria) {
        validate(criteria);
        var matched = new ArrayList<CatalogSearchCandidate>();
        var rejected = new ArrayList<CatalogSearchResult.RejectedCandidate>();
        for (var candidate : candidatePort.findByCategory(criteria.categoryId())) {
            var reasons = rejectionReasons(candidate, criteria);
            if (reasons.isEmpty()) {
                matched.add(candidate);
            } else {
                rejected.add(new CatalogSearchResult.RejectedCandidate(candidate, reasons));
            }
        }
        return new CatalogSearchResult(List.copyOf(matched), List.copyOf(rejected));
    }

    private void validate(CatalogSearchCriteria criteria) {
        if (criteria == null || criteria.categoryId() == null || criteria.categoryId().isBlank()) {
            throw new IllegalArgumentException("类目不能为空");
        }
        if (!"PHONE".equals(criteria.categoryId())) {
            throw new IllegalArgumentException("当前阶段只支持手机类目");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("最低预算不能高于最高预算");
        }
        for (var constraint : criteria.attributes()) {
            var operators = PHONE_OPERATORS.get(constraint.attribute());
            if (operators == null || !operators.contains(constraint.operator())) {
                throw new IllegalArgumentException("属性或操作符不受手机类目支持");
            }
        }
    }

    private List<String> rejectionReasons(CatalogSearchCandidate candidate, CatalogSearchCriteria criteria) {
        var reasons = new ArrayList<String>();
        if (candidate.price() == null) {
            reasons.add("PRICE_UNAVAILABLE");
        } else {
            if (criteria.minPrice() != null && candidate.price().compareTo(criteria.minPrice()) < 0) reasons.add("BELOW_MIN_PRICE");
            if (criteria.maxPrice() != null && candidate.price().compareTo(criteria.maxPrice()) > 0) reasons.add("BUDGET_EXCEEDED");
        }
        if (criteria.inStockOnly() && (!"IN_STOCK".equals(candidate.stockStatus()) || candidate.stockQuantity() <= 0)) {
            reasons.add("OUT_OF_STOCK");
        }
        for (var constraint : criteria.attributes()) {
            if (!matches(candidate.attributes().get(constraint.attribute()), constraint)) {
                reasons.add("ATTRIBUTE_CONSTRAINT_FAILED");
            }
        }
        return List.copyOf(reasons.stream().distinct().toList());
    }

    private boolean matches(Object actual, AttributeConstraint constraint) {
        if (actual == null || constraint.value() == null) return false;
        if ("=".equals(constraint.operator())) return normalized(actual).equals(normalized(constraint.value()));
        if (!(actual instanceof Number) || !(constraint.value() instanceof Number)) return false;
        int comparison = new BigDecimal(actual.toString()).compareTo(new BigDecimal(constraint.value().toString()));
        return ">=".equals(constraint.operator()) ? comparison >= 0 : comparison <= 0;
    }

    private Object normalized(Object value) {
        return value instanceof Number ? new BigDecimal(value.toString()).stripTrailingZeros() : value;
    }
}
