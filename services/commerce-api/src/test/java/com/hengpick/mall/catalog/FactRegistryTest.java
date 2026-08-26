package com.hengpick.mall.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hengpick.mall.catalog.application.FactRegistry;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.SkuDetail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FactRegistryTest {
    private final FactRegistry registry = new FactRegistry();

    @Test
    void sameProductSkusKeepTheirOwnFactsWithoutDuplicatingProductFacts() {
        var product = phoneWithTwoSkus();

        var facts = registry.register(product);

        var productFact = facts.stream().filter(fact -> fact.attribute().equals("batteryMah")).findFirst().orElseThrow();
        var storageFacts = facts.stream().filter(fact -> fact.attribute().equals("storageGb")).toList();
        assertEquals("PRODUCT", productFact.scope());
        assertEquals(2, storageFacts.size());
        assertTrue(storageFacts.stream().anyMatch(fact -> fact.skuId().equals("SKU-128") && fact.value().equals(128)));
        assertTrue(storageFacts.stream().anyMatch(fact -> fact.skuId().equals("SKU-256") && fact.value().equals(256)));
        assertFalse(storageFacts.get(0).factId().equals(storageFacts.get(1).factId()));
    }

    @Test
    void rejectsFactOwnedBySiblingSku() {
        var product = phoneWithTwoSkus();
        var fact = registry.register(product).stream()
                .filter(item -> "SKU-128".equals(item.skuId()) && item.attribute().equals("storageGb"))
                .findFirst().orElseThrow();

        var exception = assertThrows(IllegalArgumentException.class,
                () -> registry.requireReference(product, "SKU-256", fact.factId()));

        assertEquals("事实不属于目标 SKU", exception.getMessage());
    }

    private ProductDetail phoneWithTwoSkus() {
        return new ProductDetail("P-1", "PHONE", "手机", "衡选", "H1", "衡选 H1", "演示商品",
                Map.of("batteryMah", 5000), List.of(), List.of(), "全国联保",
                "commerce-demo-2026.08.1", true,
                List.of(
                        new SkuDetail("SKU-128", "H1-128", "128GB", Map.of("storageGb", 128, "color", "黑色"),
                                "IN_STOCK", 10, 12),
                        new SkuDetail("SKU-256", "H1-256", "256GB", Map.of("storageGb", 256, "color", "黑色"),
                                "IN_STOCK", 10, 12)), null);
    }
}
