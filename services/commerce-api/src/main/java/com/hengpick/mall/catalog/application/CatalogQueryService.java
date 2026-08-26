package com.hengpick.mall.catalog.application;

import com.hengpick.mall.catalog.domain.CatalogQueryPort;
import com.hengpick.mall.catalog.domain.CatalogFact;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import java.util.List;

@Service
@Profile("database")
public class CatalogQueryService {
    private final CatalogQueryPort queryPort;
    private final FactRegistry factRegistry;

    public CatalogQueryService(CatalogQueryPort queryPort, FactRegistry factRegistry) {
        this.queryPort = queryPort;
        this.factRegistry = factRegistry;
    }

    public ProductPage listProducts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数超出允许范围");
        }
        return queryPort.findProducts(page, size);
    }

    public ProductDetail getProduct(String productId, String skuId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("商品 ID 不能为空");
        }
        return queryPort.findProduct(productId, normalize(skuId)).orElseThrow(ProductNotFoundException::new);
    }

    public List<CatalogFact> getFacts(String productId, String skuId) {
        var facts = factRegistry.register(getProduct(productId, skuId));
        if (skuId == null || skuId.isBlank()) {
            return facts;
        }
        return facts.stream().filter(fact -> "PRODUCT".equals(fact.scope()) || skuId.equals(fact.skuId())).toList();
    }

    private String normalize(String skuId) {
        return skuId == null || skuId.isBlank() ? null : skuId;
    }
}
