package com.hengpick.mall.catalog.application;

import com.hengpick.mall.catalog.domain.ComparisonCandidate;
import com.hengpick.mall.catalog.domain.ComparisonMode;
import com.hengpick.mall.catalog.domain.ProductComparison;
import com.hengpick.mall.catalog.domain.ProductComparisonPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("database")
public class ProductComparisonService {
    private static final String UNKNOWN = "未知";
    private final ProductComparisonPort port;

    public ProductComparisonService(ProductComparisonPort port) {
        this.port = port;
    }

    public ProductComparison compare(List<String> skuIds, ComparisonMode mode) {
        return compare(skuIds, mode, List.of());
    }

    public ProductComparison compare(List<String> skuIds, ComparisonMode mode, List<String> relevantAttributeKeys) {
        validateRequest(skuIds, mode);
        var candidatesBySku = new LinkedHashMap<String, ComparisonCandidate>();
        for (var candidate : port.findCandidates(skuIds)) {
            candidatesBySku.put(candidate.skuId(), candidate);
        }
        if (candidatesBySku.size() != skuIds.size()) {
            throw new ProductNotFoundException();
        }
        var candidates = skuIds.stream().map(candidatesBySku::get).toList();
        var categoryId = candidates.getFirst().categoryId();
        if (candidates.stream().anyMatch(candidate -> !categoryId.equals(candidate.categoryId()))) {
            throw new IllegalArgumentException("对比商品必须属于同一类目");
        }

        var schema = port.findSchema(categoryId);
        var relevantKeys = relevantAttributeKeys == null ? java.util.Set.<String>of()
                : java.util.Set.copyOf(relevantAttributeKeys);
        var comparableKeys = schema.attributes().stream()
                .filter(attribute -> attribute.comparable())
                .map(attribute -> attribute.key())
                .collect(java.util.stream.Collectors.toSet());
        if (!comparableKeys.containsAll(relevantKeys)) {
            throw new IllegalArgumentException("需求相关属性必须来自类目可比较 Schema");
        }
        var rows = new ArrayList<ProductComparison.Row>();
        for (var attribute : schema.attributes()) {
            if (!attribute.comparable()) continue;
            if (mode == ComparisonMode.DIFFERENCES && !relevantKeys.isEmpty()
                    && !relevantKeys.contains(attribute.key())) continue;
            var values = candidates.stream()
                    .map(candidate -> candidate.attributes().getOrDefault(attribute.key(), UNKNOWN))
                    .toList();
            if (mode == ComparisonMode.DIFFERENCES && allEqual(values)) continue;
            rows.add(new ProductComparison.Row(attribute.key(), attribute.label(), attribute.unit(), values));
        }
        var products = candidates.stream()
                .map(candidate -> new ProductComparison.Product(
                        candidate.productId(), candidate.skuId(), candidate.displayName()))
                .toList();
        return new ProductComparison(categoryId, schema.schemaVersion(), mode, products, List.copyOf(rows));
    }

    private void validateRequest(List<String> skuIds, ComparisonMode mode) {
        if (skuIds == null || skuIds.size() < 2 || skuIds.size() > 4) {
            throw new IllegalArgumentException("对比商品数量必须为 2 至 4 个 SKU");
        }
        if (mode == null) throw new IllegalArgumentException("对比模式不能为空");
        if (skuIds.stream().anyMatch(skuId -> skuId == null || skuId.isBlank())) {
            throw new IllegalArgumentException("SKU ID 不能为空");
        }
        if (skuIds.stream().distinct().count() != skuIds.size()) {
            throw new IllegalArgumentException("对比列表不能包含重复 SKU");
        }
    }

    private boolean allEqual(List<Object> values) {
        var first = values.getFirst();
        return values.stream().allMatch(first::equals);
    }
}
