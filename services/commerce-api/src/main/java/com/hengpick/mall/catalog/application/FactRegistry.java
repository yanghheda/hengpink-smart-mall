package com.hengpick.mall.catalog.application;

import com.hengpick.mall.catalog.domain.CatalogFact;
import com.hengpick.mall.catalog.domain.ProductDetail;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** 从权威商品数据生成稳定事实，并执行引用作用域校验。 */
@Component
public class FactRegistry {
    public List<CatalogFact> register(ProductDetail product) {
        var facts = new ArrayList<CatalogFact>();
        product.canonicalSpecs().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> fact(product, null, "PRODUCT", entry.getKey(), entry.getValue()))
                .forEach(facts::add);
        product.skus().stream().sorted(Comparator.comparing(sku -> sku.skuId())).forEach(sku ->
                sku.attributes().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .map(entry -> fact(product, sku.skuId(), "SKU", entry.getKey(), entry.getValue()))
                        .forEach(facts::add));
        return List.copyOf(facts);
    }

    public CatalogFact requireReference(ProductDetail product, String targetSkuId, String factId) {
        if (targetSkuId == null || product.skus().stream().noneMatch(sku -> sku.skuId().equals(targetSkuId))) {
            throw new IllegalArgumentException("目标 SKU 不属于商品");
        }
        var fact = register(product).stream().filter(item -> item.factId().equals(factId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("事实不存在或数据版本不匹配"));
        if ("SKU".equals(fact.scope()) && !targetSkuId.equals(fact.skuId())) {
            throw new IllegalArgumentException("事实不属于目标 SKU");
        }
        return fact;
    }

    private CatalogFact fact(ProductDetail product, String skuId, String scope, String attribute, Object value) {
        var ownerId = skuId == null ? product.productId() : skuId;
        var source = String.join("\u001f", product.datasetVersion(), scope, ownerId, attribute);
        return new CatalogFact("FACT-" + digest(source), scope, product.productId(), skuId, attribute, value,
                product.datasetVersion());
    }

    private String digest(String source) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 10).toUpperCase(java.util.Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
