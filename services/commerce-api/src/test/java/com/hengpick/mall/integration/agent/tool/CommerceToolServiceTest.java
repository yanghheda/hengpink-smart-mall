package com.hengpick.mall.integration.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.catalog.application.CatalogQueryService;
import com.hengpick.mall.catalog.application.CatalogSearchService;
import com.hengpick.mall.catalog.application.FactRegistry;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidate;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import com.hengpick.mall.catalog.domain.SkuDetail;
import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CommerceToolServiceTest {
    private static final String DATASET = "commerce-demo-2026.08.1";
    private static final Instant NOW = Instant.parse("2026-08-25T02:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CommerceToolService service = service();

    @Test
    void fixedPhoneCandidateFlowsThroughRealCatalogPricingAndScoringDomains() {
        var search = execute("search-products", "TC-SEARCH", """
                {"categoryId":"PHONE","budget":{"max":"3000.00"},"hardConstraints":[],"limit":30}
                """);
        assertThat(objectMapper.valueToTree(search.data()).path("matchedCandidates")).hasSize(1);

        var specs = execute("get-product-specs", "TC-SPECS", """
                {"candidates":[{"productId":"P-1","skuId":"SKU-1"}]}
                """);
        assertThat(objectMapper.valueToTree(specs.data()).path("candidates").path(0).path("facts")).isNotEmpty();

        var offers = execute("get-price-offers", "TC-OFFERS", """
                {"skuIds":["SKU-1"]}
                """);
        assertThat(objectMapper.valueToTree(offers.data()).path("offers").path(0).path("salePrice").asText())
                .isEqualTo("2999.00");

        var plans = execute("calculate-final-price", "TC-PRICE", """
                {"offers":[{"offerId":"O-1","skuId":"SKU-1","salePrice":"0.01"}],"memberships":[]}
                """);
        assertThat(objectMapper.valueToTree(plans.data()).path("pricePlans").path("SKU-1")
                .path(0).path("finalPrice").asText()).isEqualTo("2999.00");

        var scores = execute("score-candidates", "TC-SCORE", """
                {"intent":{"category":"PHONE"},
                 "candidates":[{"productId":"P-1","skuId":"SKU-1","attributes":{"storageGb":256}}],
                 "pricePlans":{"SKU-1":[{"finalPrice":"2999.00"}]}}
                """);
        var score = objectMapper.valueToTree(scores.data()).path("scoreCards").path(0).path("finalScore").asText();
        assertThat(new BigDecimal(score)).isPositive();
    }

    @Test
    void toolCallIdIsIdempotentAndRejectsChangedPayload() {
        var first = execute("search-products", "TC-SAME", """
                {"categoryId":"PHONE","budget":{"max":"3000.00"},"hardConstraints":[]}
                """);
        var repeated = execute("search-products", "TC-SAME", """
                {"categoryId":"PHONE","budget":{"max":"3000.00"},"hardConstraints":[]}
                """);
        assertThat(repeated).isSameAs(first);

        assertThatThrownBy(() -> execute("search-products", "TC-SAME", """
                {"categoryId":"PHONE","budget":{"max":"2000.00"},"hardConstraints":[]}
                """)).isInstanceOf(IllegalStateException.class).hasMessageContaining("请求内容不一致");
    }

    @Test
    void internalToolRejectsMissingServiceCredential() throws Exception {
        var controller = new CommerceToolController(service, "service-secret");
        var request = new ToolRequestEnvelope(
                "RUN-1", 1, "TC-AUTH", DATASET, 1000,
                objectMapper.readTree("""
                        {"categoryId":"PHONE","budget":{"max":"3000.00"},"hardConstraints":[]}
                        """));

        assertThatThrownBy(() -> controller.execute("search-products", null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        assertThat(controller.execute("search-products", "Bearer service-secret", request)
                .getBody().status()).isEqualTo("SUCCESS");
    }

    private ToolResponseEnvelope execute(String tool, String callId, String inputJson) {
        try {
            return service.execute(tool, new ToolRequestEnvelope(
                    "RUN-1", 1, callId, DATASET, 1000, objectMapper.readTree(inputJson)));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CommerceToolService service() {
        var attributes = Map.<String, Object>of("storageGb", 256L, "batteryMah", 5000L);
        var candidate = new CatalogSearchCandidate(
                "P-1", "SKU-1", "长辈手机 256GB", "PHONE", new BigDecimal("2999.00"),
                "IN_STOCK", 10, attributes);
        var sku = new SkuDetail("SKU-1", "PHONE-1", "256GB", attributes, "IN_STOCK", 10, 24);
        var product = new ProductDetail(
                "P-1", "PHONE", "手机", "衡选", "Care One", "长辈手机", "",
                Map.of("simpleMode", true), List.of(), List.of(), "两年保修", DATASET, true,
                List.of(sku), sku);
        var searchService = new CatalogSearchService(category -> List.of(candidate));
        var queryService = new CatalogQueryService(new com.hengpick.mall.catalog.domain.CatalogQueryPort() {
            @Override
            public ProductPage findProducts(int page, int size) {
                return new ProductPage(List.of(), page, size, 0);
            }

            @Override
            public Optional<ProductDetail> findProduct(String productId, String skuId) {
                return "P-1".equals(productId) && (skuId == null || "SKU-1".equals(skuId))
                        ? Optional.of(product) : Optional.empty();
            }
        }, new FactRegistry());
        var offer = new Offer(
                "O-1", "SKU-1", "SHOP-1", new Money(new BigDecimal("3199.00"), "CNY"),
                new Money(new BigDecimal("2999.00"), "CNY"),
                new Money(new BigDecimal("0.00"), "CNY"),
                NOW.minusSeconds(60), NOW.plusSeconds(60), DATASET, 1);
        var offerService = new OfferQueryService((skuId, calculationAt) -> List.of(offer),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new CommerceToolService(
                searchService, queryService, offerService, new RecommendationScorer(),
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
