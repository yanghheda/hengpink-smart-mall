package com.hengpick.mall.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.catalog.domain.CatalogQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
@ActiveProfiles("database")
@SpringBootTest
class CatalogMapperIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("hengpick_catalog_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("MYSQL_URL", mysql::getJdbcUrl);
        registry.add("MYSQL_USERNAME", mysql::getUsername);
        registry.add("MYSQL_PASSWORD", mysql::getPassword);
        registry.add("REDIS_URL", () -> "redis://localhost:6379");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CatalogQueryPort queryPort;

    @BeforeEach
    void insertFixture() {
        jdbc.update("DELETE FROM skus");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("""
                INSERT INTO categories
                  (id, code, name, depth_level, schema_version, schema_json, status, created_at, updated_at)
                VALUES (?, 'PHONE', '手机', 1, 'phone-1.0', '{}', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, "01JCAT00000000000000000001");
        insertProduct("01JPROD000000000000000001", "衡选 H1", "2026-08-26 00:00:01");
        insertProduct("01JPROD000000000000000002", "衡选 H2", "2026-08-26 00:00:02");
        insertSku("01JSKU00000000000000000001", "01JPROD000000000000000001", "128GB", 128);
        insertSku("01JSKU00000000000000000002", "01JPROD000000000000000001", "256GB", 256);
        insertSku("01JSKU00000000000000000003", "01JPROD000000000000000002", "256GB", 256);
    }

    @Test
    void mapperPaginatesProductsAndCountsOnlyTheirOwnSkus() {
        var page = queryPort.findProducts(0, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).singleElement().satisfies(product -> {
            assertThat(product.productId()).isEqualTo("01JPROD000000000000000002");
            assertThat(product.skuCount()).isEqualTo(1);
            assertThat(product.simulated()).isTrue();
        });
    }

    @Test
    void detailKeepsSkuAttributesWithinTheirProductBoundary() {
        var detail = queryPort.findProduct(
                "01JPROD000000000000000001", "01JSKU00000000000000000002");
        var mismatch = queryPort.findProduct(
                "01JPROD000000000000000001", "01JSKU00000000000000000003");

        assertThat(detail).get().satisfies(product -> {
            assertThat(product.skus()).hasSize(2);
            assertThat(product.selectedSku().attributes()).containsEntry("storageGb", 256);
        });
        assertThat(mismatch).isEmpty();
    }

    private void insertProduct(String id, String displayName, String createdAt) {
        jdbc.update("""
                INSERT INTO products
                  (id, category_id, brand, model, canonical_variant, display_name, subtitle,
                   canonical_specs_json, selling_points_json, limitation_json, warranty_summary,
                   dataset_version, is_simulated, status, created_at, updated_at)
                VALUES (?, '01JCAT00000000000000000001', '衡选', ?, ?, ?, '演示商品',
                        '{"batteryMah":5000}', '["续航稳定"]', '[]', '一年保修',
                        'commerce-demo-2026.08.1', 1, 'ACTIVE', ?, ?)
                """, id, displayName, id, displayName, createdAt, createdAt);
    }

    private void insertSku(String id, String productId, String displayName, int storageGb) {
        jdbc.update("""
                INSERT INTO skus
                  (id, product_id, sku_code, display_name, attributes_json, stock_status,
                   stock_quantity, warranty_months, dataset_version, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, JSON_OBJECT('storageGb', ?), 'IN_STOCK', 3, 12,
                        'commerce-demo-2026.08.1', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, id, productId, id, displayName, storageGb);
    }
}
