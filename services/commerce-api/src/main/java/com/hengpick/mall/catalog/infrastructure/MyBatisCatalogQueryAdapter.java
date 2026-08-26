package com.hengpick.mall.catalog.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.catalog.domain.CatalogQueryPort;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import com.hengpick.mall.catalog.domain.ProductSummary;
import com.hengpick.mall.catalog.domain.SkuDetail;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("database")
public class MyBatisCatalogQueryAdapter implements CatalogQueryPort {
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
