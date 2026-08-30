package com.hengpick.mall.catalog.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.catalog.domain.CatalogQueryPort;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidate;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidatePort;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import com.hengpick.mall.catalog.domain.ProductSummary;
import com.hengpick.mall.catalog.domain.ProductComparisonPort;
import com.hengpick.mall.catalog.domain.ComparisonCandidate;
import com.hengpick.mall.catalog.domain.CategoryComparisonSchema;
import com.hengpick.mall.catalog.domain.SkuDetail;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("database")
public class MyBatisCatalogQueryAdapter implements CatalogQueryPort, CatalogSearchCandidatePort, ProductComparisonPort {
    private final CatalogMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisCatalogQueryAdapter(CatalogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }
    @Override
    public ProductPage findProducts(int page, int size) {
        var items = mapper.findPage(page * size, size).stream().map(this::toSummary).toList();
        return new ProductPage(items, page, size, mapper.countActive());
    }

    @Override
    public Optional<ProductDetail> findProduct(String productId, String skuId) {
        var product = mapper.findProduct(productId);
        if (product == null) {
            return Optional.empty();
        }
        var skus = mapper.findSkus(productId).stream().map(this::toSku).toList();
        var selected = skuId == null
                ? null
                : skus.stream().filter(sku -> sku.skuId().equals(skuId)).findFirst().orElse(null);
        if (skuId != null && selected == null) {
            return Optional.empty();
        }
        return Optional.of(new ProductDetail(
                product.productId(), product.categoryId(), product.categoryName(), product.brand(), product.model(),
                product.displayName(), product.subtitle(), readMap(product.canonicalSpecsJson()),
                readList(product.sellingPointsJson()), readList(product.limitationJson()), product.warrantySummary(),
                product.datasetVersion(), product.simulated(), skus, selected));
    }

    @Override
    public List<CatalogSearchCandidate> findByCategory(String categoryId) {
        return mapper.findSearchCandidates(categoryId).stream().map(row -> {
            var attributes = new java.util.HashMap<>(readMap(row.canonicalSpecsJson()));
            attributes.putAll(readMap(row.attributesJson()));
            return new CatalogSearchCandidate(row.productId(), row.skuId(), row.displayName(), row.categoryId(),
                    row.price(), row.stockStatus(), row.stockQuantity(), Map.copyOf(attributes));
        }).toList();
    }

    @Override
    public List<ComparisonCandidate> findCandidates(List<String> skuIds) {
        return mapper.findComparisonCandidates(skuIds).stream().map(row -> {
            var attributes = new java.util.HashMap<>(readMap(row.canonicalSpecsJson()));
            attributes.putAll(readMap(row.attributesJson()));
            return new ComparisonCandidate(
                    row.productId(), row.skuId(), row.displayName(), row.categoryId(), Map.copyOf(attributes));
        }).toList();
    }

    @Override
    public CategoryComparisonSchema findSchema(String categoryId) {
        var row = mapper.findCategorySchema(categoryId);
        if (row == null) throw new IllegalArgumentException("类目 Schema 不存在");
        try {
            var root = objectMapper.readTree(row.schemaJson());
            var attributes = new java.util.ArrayList<CategoryComparisonSchema.Attribute>();
            for (var node : root.withArray("attributes")) {
                if (!node.isObject()) continue;
                attributes.add(new CategoryComparisonSchema.Attribute(
                        node.path("key").asText(), node.path("label").asText(),
                        node.path("unit").isNull() ? null : node.path("unit").asText(),
                        node.path("comparable").asBoolean(false)));
            }
            if (attributes.isEmpty()) throw new IllegalArgumentException("类目 Schema 缺少可比较属性定义");
            return new CategoryComparisonSchema(row.categoryId(), row.schemaVersion(), List.copyOf(attributes));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("类目 Schema 无法解析", exception);
        }
    }

    private ProductSummary toSummary(ProductRow row) {
        return new ProductSummary(row.productId(), row.categoryId(), row.categoryName(), row.brand(), row.model(),
                row.displayName(), row.subtitle(), row.datasetVersion(), row.simulated(), row.skuCount());
    }

    private SkuDetail toSku(SkuRow row) {
        return new SkuDetail(row.skuId(), row.skuCode(), row.displayName(), readMap(row.attributesJson()),
                row.stockStatus(), row.stockQuantity(), row.warrantyMonths());
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("商品 JSON 数据无法解析", exception);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("商品 JSON 数据无法解析", exception);
        }
    }
}
