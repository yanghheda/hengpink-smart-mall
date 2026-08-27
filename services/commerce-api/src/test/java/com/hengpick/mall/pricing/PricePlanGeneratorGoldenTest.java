package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.domain.plan.PricePlanGenerator;
import com.hengpick.mall.pricing.domain.plan.PricePlanType;
import com.hengpick.mall.pricing.domain.promotion.CompiledPromotion;
import com.hengpick.mall.pricing.domain.promotion.PromotionApplicationContext;
import com.hengpick.mall.pricing.domain.promotion.PromotionCombinationSearcher;
import com.hengpick.mall.pricing.domain.promotion.PromotionCompiler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricePlanGeneratorGoldenTest {

    private static final Instant CALCULATION_AT = Instant.parse("2026-08-27T02:00:00Z");
    private static final String PRICING_RULE_VERSION = "pricing-v1";

    private PromotionCompiler compiler;
    private PricePlanGenerator generator;

    @BeforeEach
    void setUp() {
        compiler = new PromotionCompiler(new ObjectMapper());
        generator = new PricePlanGenerator(new PromotionCombinationSearcher());
    }

    @Test
    void goldenSelectsThreeObjectivesAndKeepsEveryAmountReproducible() {
        var offer = offer("3099.00", "20.00");
        var context = context(offer, Set.of("SMART_MALL_PLUS"));
        var promotions = List.of(
                promotion("DIRECT", "DIRECT_REDUCTION", 30,
                        "[\"FULL_REDUCTION\", \"PRODUCT_COUPON\", \"MEMBER_DISCOUNT\"]", """
                        "benefit": {"amountOff": "100.00"}
                        """),
                promotion("FULL", "FULL_REDUCTION", 20,
                        "[\"DIRECT_REDUCTION\", \"PRODUCT_COUPON\", \"MEMBER_DISCOUNT\"]", """
                        "condition": {"minAmount": "2999.00"},
                        "benefit": {"amountOff": "200.00"}
                        """),
                promotion("COUPON", "PRODUCT_COUPON", 10,
                        "[\"DIRECT_REDUCTION\", \"FULL_REDUCTION\", \"MEMBER_DISCOUNT\"]", """
                        "benefit": {"amountOff": "50.00"}
                        """),
                promotion("MEMBER", "MEMBER_DISCOUNT", 5,
                        "[\"DIRECT_REDUCTION\", \"FULL_REDUCTION\", \"PRODUCT_COUPON\"]", """
                        "condition": {"membership": "SMART_MALL_PLUS"},
                        "benefit": {"discountRate": "0.95"}
                        """));

        var plans = generator.generate(context, promotions, PRICING_RULE_VERSION);

        assertThat(plans).extracting(plan -> plan.type())
                .containsExactly(PricePlanType.LOWEST_PRICE, PricePlanType.NO_MEMBERSHIP, PricePlanType.MIN_STEPS);
        assertThat(plans).filteredOn(plan -> plan.type() == PricePlanType.LOWEST_PRICE).singleElement().satisfies(plan -> {
            assertThat(plan.appliedPromotionIds()).containsExactly("DIRECT", "FULL", "COUPON", "MEMBER");
            assertThat(plan.steps()).extracting(step -> step.afterAmount().toString())
                    .containsExactly("2999.00", "2799.00", "2749.00", "2611.55");
            assertThat(plan.finalPrice().toString()).isEqualTo("2631.55");
            assertThat(plan.requirements()).containsExactly("SMART_MALL_PLUS");
        });
        assertThat(plans).filteredOn(plan -> plan.type() == PricePlanType.NO_MEMBERSHIP).singleElement().satisfies(plan -> {
            assertThat(plan.appliedPromotionIds()).containsExactly("DIRECT", "FULL", "COUPON");
            assertThat(plan.finalPrice().toString()).isEqualTo("2769.00");
            assertThat(plan.requirements()).isEmpty();
        });
        assertThat(plans).filteredOn(plan -> plan.type() == PricePlanType.MIN_STEPS).singleElement().satisfies(plan -> {
            assertThat(plan.appliedPromotionIds()).containsExactly("FULL");
            assertThat(plan.finalPrice().toString()).isEqualTo("2919.00");
        });
    }

    @Test
    void goldenFallsBackToBaseOfferWhenNoPromotionCanApply() {
        var offer = offer("999.00", "12.00");
        var context = context(offer, Set.of());
        var memberOnly = promotion("MEMBER", "MEMBER_DISCOUNT", 1, "[]", """
                "condition": {"membership": "SMART_MALL_PLUS"},
                "benefit": {"discountRate": "0.90"}
                """);

        var plans = generator.generate(context, List.of(memberOnly), PRICING_RULE_VERSION);

        assertThat(plans).hasSize(3).allSatisfy(plan -> {
            assertThat(plan.appliedPromotionIds()).isEmpty();
            assertThat(plan.steps()).isEmpty();
            assertThat(plan.finalPrice().toString()).isEqualTo("1011.00");
            assertThat(plan.requirements()).isEmpty();
        });
    }

    @Test
    void goldenUsesStableTieBreakAndCapturesTheWholeCalculationSnapshot() {
        var offer = offer("1000.00", "0.00");
        var context = context(offer, Set.of());
        var promotionB = promotion("B", "DIRECT_REDUCTION", 1, "[]", """
                "benefit": {"amountOff": "100.00"}
                """);
        var promotionA = promotion("A", "DIRECT_REDUCTION", 1, "[]", """
                "benefit": {"amountOff": "100.00"}
                """);

        var plans = generator.generate(context, List.of(promotionB, promotionA), PRICING_RULE_VERSION);

        assertThat(plans).allSatisfy(plan -> {
            assertThat(plan.appliedPromotionIds()).containsExactly("A");
            assertThat(plan.offerId()).isEqualTo("O-1");
            assertThat(plan.offerVersion()).isEqualTo(7);
            assertThat(plan.datasetVersion()).isEqualTo("commerce-demo-2026.08.1");
            assertThat(plan.pricingRuleVersion()).isEqualTo(PRICING_RULE_VERSION);
            assertThat(plan.calculationAt()).isEqualTo(CALCULATION_AT);
            assertThat(plan.memberships()).isEmpty();
        });
    }

    @Test
    void rejectsAContextWhoseStartingAmountHasDriftedFromTheOffer() {
        var offer = offer("1000.00", "0.00");
        var driftedContext = new PromotionApplicationContext(
                offer, "PRODUCT-1", "PHONE", Money.cny("999.00"), Set.of(), CALCULATION_AT);

        assertThatThrownBy(() -> generator.generate(driftedContext, List.of(), PRICING_RULE_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须从报价销售价开始");
    }

    private Offer offer(String salePrice, String additionalFee) {
        return new Offer(
                "O-1", "SKU-1", "SHOP-1",
                Money.cny(salePrice), Money.cny(salePrice), Money.cny(additionalFee),
                Instant.parse("2026-08-27T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"),
                "commerce-demo-2026.08.1", 7);
    }

    private PromotionApplicationContext context(Offer offer, Set<String> memberships) {
        return new PromotionApplicationContext(
                offer, "PRODUCT-1", "PHONE", offer.salePrice(), memberships, CALCULATION_AT);
    }

    private CompiledPromotion promotion(
            String id,
            String type,
            int priority,
            String stackableWithTypes,
            String body) {
        return compiler.compile("""
                {
                  "promotionId": "%s",
                  "type": "%s",
                  "priority": %d,
                  "stackableWithTypes": %s,
                  "exclusiveWithIds": [],
                  %s
                }
                """.formatted(id, type, priority, stackableWithTypes, body));
    }
}
