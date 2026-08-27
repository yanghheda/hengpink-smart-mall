package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.domain.promotion.PromotionApplicationContext;
import com.hengpick.mall.pricing.domain.promotion.PromotionCombinationSearcher;
import com.hengpick.mall.pricing.domain.promotion.PromotionCompiler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromotionCombinationSearcherTest {

    private static final Instant CALCULATION_AT = Instant.parse("2026-08-27T02:00:00Z");

    private PromotionCompiler compiler;
    private PromotionApplicationContext context;

    @BeforeEach
    void setUp() {
        compiler = new PromotionCompiler(new ObjectMapper());
        var offer = new Offer(
                "O-1", "SKU-1", "SHOP-1",
                Money.cny("3099.00"), Money.cny("3099.00"), Money.cny("0.00"),
                Instant.parse("2026-08-27T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"),
                "commerce-demo-2026.08.1", 0);
        context = new PromotionApplicationContext(
                offer, "PRODUCT-1", "PHONE", offer.salePrice(), Set.of("SMART_MALL_PLUS"), CALCULATION_AT);
    }

    @Test
    void appliesAStackableCombinationInStableBusinessOrder() {
        var member = promotion("MEMBER", "MEMBER_DISCOUNT", 10, "[\"FULL_REDUCTION\"]", "[]", """
                "condition": {"membership": "SMART_MALL_PLUS"},
                "benefit": {"discountRate": "0.95"}
                """);
        var reduction = promotion("FULL", "FULL_REDUCTION", 20, "[\"MEMBER_DISCOUNT\"]", "[]", """
                "condition": {"minAmount": "3000.00"},
                "benefit": {"amountOff": "200.00"}
                """);

        var combinations = new PromotionCombinationSearcher().search(List.of(member, reduction), context);

        assertThat(combinations).anySatisfy(combination -> {
            assertThat(combination.promotionIds()).containsExactly("FULL", "MEMBER");
            assertThat(combination.steps()).extracting(step -> step.afterAmount().toString())
                    .containsExactly("2899.00", "2754.05");
            assertThat(combination.finalAmount().toString()).isEqualTo("2754.05");
        });
    }

    @Test
    void neverExecutesAnExclusivePair() {
        var direct = promotion("DIRECT", "DIRECT_REDUCTION", 10, "[\"PRODUCT_COUPON\"]", "[\"COUPON\"]", """
                "benefit": {"amountOff": "100.00"}
                """);
        var coupon = promotion("COUPON", "PRODUCT_COUPON", 10, "[\"DIRECT_REDUCTION\"]", "[]", """
                "benefit": {"amountOff": "50.00"}
                """);

        var combinations = new PromotionCombinationSearcher().search(List.of(direct, coupon), context);

        assertThat(combinations).extracting(combination -> combination.promotionIds().size())
                .containsOnly(1);
    }

    @Test
    void rejectsDirtyConflictGraphsBeforeSearching() {
        var selfExclusive = promotion("SELF", "DIRECT_REDUCTION", 1, "[]", "[\"SELF\"]", """
                "benefit": {"amountOff": "10.00"}
                """);
        var unknownTarget = promotion("UNKNOWN", "DIRECT_REDUCTION", 1, "[]", "[\"MISSING\"]", """
                "benefit": {"amountOff": "10.00"}
                """);

        assertThatThrownBy(() -> new PromotionCombinationSearcher().search(List.of(selfExclusive), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能与自身互斥");
        assertThatThrownBy(() -> new PromotionCombinationSearcher().search(List.of(unknownTarget), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在的互斥优惠");
    }

    @Test
    void rejectsAsymmetricStackingDeclarations() {
        var direct = promotion("DIRECT", "DIRECT_REDUCTION", 1, "[\"PRODUCT_COUPON\"]", "[]", """
                "benefit": {"amountOff": "10.00"}
                """);
        var coupon = promotion("COUPON", "PRODUCT_COUPON", 1, "[]", "[]", """
                "benefit": {"amountOff": "10.00"}
                """);

        assertThatThrownBy(() -> new PromotionCombinationSearcher().search(List.of(direct, coupon), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("叠加声明必须双向一致");
    }

    @Test
    void filtersInapplicableRulesAndDeduplicatesEquivalentResultsStably() {
        var first = promotion("A", "DIRECT_REDUCTION", 1, "[]", "[]", """
                "benefit": {"amountOff": "100.00"}
                """);
        var equivalent = promotion("B", "DIRECT_REDUCTION", 1, "[]", "[]", """
                "benefit": {"amountOff": "100.00"}
                """);
        var expired = promotion("EXPIRED", "DIRECT_REDUCTION", 1, "[]", "[]", """
                "condition": {"timeWindow": {
                  "start": "2026-08-27T00:00:00Z",
                  "end": "2026-08-27T01:00:00Z"
                }},
                "benefit": {"amountOff": "500.00"}
                """);

        var combinations = new PromotionCombinationSearcher().search(List.of(expired, equivalent, first), context);

        assertThat(combinations).singleElement().satisfies(combination -> {
            assertThat(combination.promotionIds()).containsExactly("A");
            assertThat(combination.finalAmount().toString()).isEqualTo("2999.00");
        });
    }

    @Test
    void stopsInsteadOfReturningAnUnprovenResultWhenTheLimitIsExceeded() {
        var promotions = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> promotion(
                        "P" + index,
                        "DIRECT_REDUCTION",
                        index,
                        "[\"DIRECT_REDUCTION\"]",
                        "[]",
                        "\"benefit\": {\"amountOff\": \"1.00\"}"))
                .toList();

        assertThatThrownBy(() -> new PromotionCombinationSearcher(20).search(promotions, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("合法优惠组合超过上限 20");
    }

    private com.hengpick.mall.pricing.domain.promotion.CompiledPromotion promotion(
            String id,
            String type,
            int priority,
            String stackableWithTypes,
            String exclusiveWithIds,
            String body) {
        return compiler.compile("""
                {
                  "promotionId": "%s",
                  "type": "%s",
                  "priority": %d,
                  "stackableWithTypes": %s,
                  "exclusiveWithIds": %s,
                  %s
                }
                """.formatted(id, type, priority, stackableWithTypes, exclusiveWithIds, body));
    }
}
