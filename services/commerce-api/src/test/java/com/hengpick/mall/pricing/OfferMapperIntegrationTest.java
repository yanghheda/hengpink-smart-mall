package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.pricing.domain.OfferQueryPort;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "VM_DATABASE_INTEGRATION", matches = "true")
@ActiveProfiles("database")
@SpringBootTest
class OfferMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OfferQueryPort offerQueryPort;

    @BeforeEach
    void insertFixture() {
        jdbc.update("DELETE FROM offers");
        jdbc.update("DELETE FROM shops");
        jdbc.update("DELETE FROM skus");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("""
                INSERT INTO categories
                  (id, code, name, depth_level, schema_version, schema_json, status, created_at, updated_at)
                VALUES ('01JCAT00000000000000000001', 'PHONE', '手机', 1, 'phone-1.0', '{}', 'ACTIVE',
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """);
        jdbc.update("""
                INSERT INTO products
                  (id, category_id, brand, model, canonical_variant, display_name, canonical_specs_json,
                   selling_points_json, limitation_json, dataset_version, is_simulated, status, created_at, updated_at)
                VALUES ('01JPROD000000000000000001', '01JCAT00000000000000000001', '衡选', 'H1', 'H1', '衡选 H1',
                        '{}', '[]', '[]', 'commerce-demo-2026.08.1', 1, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """);
        jdbc.update("""
                INSERT INTO skus
                  (id, product_id, sku_code, display_name, attributes_json, stock_status, stock_quantity,
                   warranty_months, dataset_version, status, created_at, updated_at)
                VALUES ('01JSKU00000000000000000001', '01JPROD000000000000000001', 'SKU-1', '256GB', '{}',
                        'IN_STOCK', 1, 12, 'commerce-demo-2026.08.1', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """);
        jdbc.update("""
                INSERT INTO shops (id, name, dataset_version, status, created_at, updated_at)
                VALUES ('SHOP-1', '演示店铺', 'commerce-demo-2026.08.1', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """);
        insertOffer("O-START", "ACTIVE", "2026-08-27 02:00:00", "2026-08-28 00:00:00");
        insertOffer("O-END", "ACTIVE", "2026-08-26 00:00:00", "2026-08-27 02:00:00");
        insertOffer("O-INACTIVE", "INACTIVE", "2026-08-26 00:00:00", "2026-08-28 00:00:00");
    }

    @Test
    void queryReturnsOnlyActiveOffersInsideTheHalfOpenInterval() {
        var offers = offerQueryPort.findValidOffers(
                "01JSKU00000000000000000001", Instant.parse("2026-08-27T02:00:00Z"));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.offerId()).isEqualTo("O-START");
            assertThat(offer.salePrice().toString()).isEqualTo("2999.00");
        });
    }

    private void insertOffer(String id, String status, String validFrom, String validTo) {
        jdbc.update("""
                INSERT INTO offers
                  (id, sku_id, shop_id, list_price, sale_price, additional_fee, currency, stock_status,
                   valid_from, valid_to, dataset_version, status, version, created_at, updated_at)
                VALUES (?, '01JSKU00000000000000000001', 'SHOP-1', 3099.00, 2999.00, 0.00, 'CNY', 'IN_STOCK',
                        ?, ?, 'commerce-demo-2026.08.1', ?, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, id, validFrom, validTo, status);
    }
}
