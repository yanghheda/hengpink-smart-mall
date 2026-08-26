package com.hengpick.mall.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.catalog.application.CatalogQueryService;
import com.hengpick.mall.catalog.domain.CatalogQueryPort;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import com.hengpick.mall.catalog.domain.ProductSummary;
import com.hengpick.mall.catalog.domain.SkuDetail;
import com.hengpick.mall.catalog.web.CatalogController;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CatalogQueryPort port = new CatalogQueryPort() {
            @Override
            public ProductPage findProducts(int page, int size) {
                return new ProductPage(
                        List.of(new ProductSummary("P-1", "PHONE", "手机", "衡选", "H1", "衡选 H1", "演示商品", "commerce-demo-2026.08.1", true, 2)),
                        page,
                        size,
                        1);
            }

            @Override
            public Optional<ProductDetail> findProduct(String productId, String skuId) {
                if (!productId.equals("P-1") || "S-WRONG".equals(skuId)) {
                    return Optional.empty();
                }
                var sku = new SkuDetail("S-1", "SKU-1", "8GB+256GB", Map.of("storageGb", 256), "IN_STOCK", 3, 12);
                return Optional.of(new ProductDetail(
                        "P-1", "PHONE", "手机", "衡选", "H1", "衡选 H1", "演示商品",
                        Map.of("batteryMah", 5000), List.of("续航稳定"), List.of(), "一年保修",
                        "commerce-demo-2026.08.1", true, List.of(sku), sku));
            }
        };
        var clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
        var controller = new CatalogController(new CatalogQueryService(port), clock);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CatalogExceptionHandler())
                .build();
    }

    @Test
    void listsProductsWithStablePaginationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value("P-1"))
                .andExpect(jsonPath("$.data.items[0].skuCount").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.meta.serverTime").value("2026-08-26T00:00:00Z"));
    }

    @Test
    void returnsSelectedSkuInProductDetail() throws Exception {
        mockMvc.perform(get("/api/v1/products/P-1").param("skuId", "S-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value("P-1"))
                .andExpect(jsonPath("$.data.selectedSku.skuId").value("S-1"))
                .andExpect(jsonPath("$.data.skus[0].attributes.storageGb").value(256));
    }

    @Test
    void rejectsSkuThatDoesNotBelongToProductAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/P-1").param("skuId", "S-WRONG"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void rejectsPaginationOutsideTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATALOG_QUERY_INVALID"));
    }
}
