package com.hengpick.mall.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.pricing.domain.Money;
import com.hengpick.mall.pricing.domain.Offer;
import com.hengpick.mall.pricing.domain.promotion.PromotionApplicationContext;
import com.hengpick.mall.pricing.domain.promotion.PromotionCompiler;
import com.hengpick.mall.pricing.domain.promotion.PromotionRejectionReason;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromotionCompilerTest {

    private static final Instant CALCULATION_AT = Instant.parse("2026-08-27T02:00:00Z");

    private PromotionCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new PromotionCompiler(new ObjectMapper());
    }

    @Test
    void directReductionAppliesAndCapsTheDiscountAtTheCurrentAmount() {
        var promotion = compiler.compile(rule("DIRECT_REDUCTION", """
                "benefit": {"amountOff": "300.00"}
                """));

        var result = promotion.apply(context("200.00", Set.of()));

        assertThat(result.applied()).isTrue();
        assertThat(result.step()).isPresent().get().satisfies(step -> {
            assertThat(step.beforeAmount().toString()).isEqualTo("200.00");
            assertThat(step.discountAmount().toString()).isEqualTo("200.00");
            assertThat(step.afterAmount().toString()).isEqualTo("0.00");
        });
    }

    @Test
    void directReductionRejectsAnotherSkuScope() {
        var promotion = compiler.compile(rule("DIRECT_REDUCTION", """
                "scope": {"skuIds": ["SKU-OTHER"]},
                "benefit": {"amountOff": "100.00"}
                """));

        var result = promotion.apply(context("3099.00", Set.of()));

        assertThat(result.applied()).isFalse();
        assertThat(result.rejectionReason()).contains(PromotionRejectionReason.SCOPE_NOT_MATCHED);
    }

    @Test
    void fullReductionUsesTheAmountBeforeThisRuleAndIncludesTheThreshold() {
        var promotion = compiler.compile(rule("FULL_REDUCTION", """
                "condition": {"minAmount": "3000.00"},
                "benefit": {"amountOff": "200.00"}
                """));

        var boundary = promotion.apply(context("3000.00", Set.of()));
        var below = promotion.apply(context("2999.99", Set.of()));

        assertThat(boundary.step()).isPresent().get()
                .extracting(step -> step.afterAmount().toString()).isEqualTo("2800.00");
        assertThat(below.rejectionReason()).contains(PromotionRejectionReason.THRESHOLD_NOT_MET);
    }

    @Test
    void productCouponSupportsAPercentageAndRoundsAtThisStep() {
        var promotion = compiler.compile(rule("PRODUCT_COUPON", """
                "benefit": {"discountRate": "0.95"}
                """));

        var result = promotion.apply(context("2799.00", Set.of()));

        assertThat(result.step()).isPresent().get().satisfies(step -> {
            assertThat(step.discountAmount().toString()).isEqualTo("139.95");
            assertThat(step.afterAmount().toString()).isEqualTo("2659.05");
        });
    }

    @Test
    void productCouponIsNotAppliedAtTheExclusiveEndTime() {
        var promotion = compiler.compile(rule("PRODUCT_COUPON", """
                "condition": {"timeWindow": {
                  "start": "2026-08-27T00:00:00Z",
                  "end": "2026-08-27T02:00:00Z"
                }},
                "benefit": {"amountOff": "100.00"}
                """));

        var result = promotion.apply(context("2999.00", Set.of()));

        assertThat(result.rejectionReason()).contains(PromotionRejectionReason.EXPIRED);
    }

    @Test
    void memberDiscountRequiresTheConfiguredMembership() {
        var promotion = compiler.compile(rule("MEMBER_DISCOUNT", """
                "condition": {"membership": "SMART_MALL_PLUS"},
                "benefit": {"discountRate": "0.95"}
                """));

        var eligible = promotion.apply(context("100.00", Set.of("SMART_MALL_PLUS")));
        var ineligible = promotion.apply(context("100.00", Set.of()));

        assertThat(eligible.step()).isPresent().get()
                .extracting(step -> step.afterAmount().toString()).isEqualTo("95.00");
        assertThat(ineligible.rejectionReason()).contains(PromotionRejectionReason.MEMBERSHIP_REQUIRED);
    }

    @Test
    void futurePromotionIsNotApplicableYet() {
        var promotion = compiler.compile(rule("DIRECT_REDUCTION", """
                "condition": {"timeWindow": {
                  "start": "2026-08-27T03:00:00Z",
                  "end": "2026-08-27T04:00:00Z"
                }},
                "benefit": {"amountOff": "100.00"}
                """));

        assertThat(promotion.apply(context("2999.00", Set.of())).rejectionReason())
                .contains(PromotionRejectionReason.NOT_STARTED);
    }

    @Test
    void everySupportedTypeUsesTheSameScopeAndHalfOpenTimeBoundaries() {
        var supportedTypes = Set.of(
                "DIRECT_REDUCTION", "FULL_REDUCTION", "PRODUCT_COUPON", "MEMBER_DISCOUNT");

        for (var type : supportedTypes) {
            var conditionFields = switch (type) {
                case "FULL_REDUCTION" -> "\"minAmount\": \"2999.00\",";
                case "MEMBER_DISCOUNT" -> "\"membership\": \"SMART_MALL_PLUS\",";
                default -> "";
            };
            var promotion = compiler.compile(rule(type, """
                    "scope": {"skuIds": ["SKU-1"]},
                    "condition": {%s "timeWindow": {
                      "start": "2026-08-27T02:00:00Z",
                      "end": "2026-08-27T03:00:00Z"
                    }},
                    "benefit": {"amountOff": "10.00"}
                    """.formatted(conditionFields)));

            assertThat(promotion.apply(contextAt("2999.00", Set.of("SMART_MALL_PLUS"), CALCULATION_AT)).applied())
                    .as(type + " 在开始边界应适用")
                    .isTrue();
            assertThat(promotion.apply(contextAt(
                            "2999.00", Set.of("SMART_MALL_PLUS"), Instant.parse("2026-08-27T03:00:00Z")))
                    .rejectionReason()).as(type + " 在结束边界应过期")
                    .contains(PromotionRejectionReason.EXPIRED);
        }
    }

    @Test
    void compilerRejectsUnknownTypesAndAmbiguousBenefits() {
        assertThatThrownBy(() -> compiler.compile(rule("GIFT", """
                "benefit": {"amountOff": "10.00"}
                """))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的优惠类型");

        assertThatThrownBy(() -> compiler.compile(rule("PRODUCT_COUPON", """
                "benefit": {"amountOff": "10.00", "discountRate": "0.95"}
                """))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能配置一种优惠权益");
    }

    private PromotionApplicationContext context(String currentAmount, Set<String> memberships) {
        return contextAt(currentAmount, memberships, CALCULATION_AT);
    }

    private PromotionApplicationContext contextAt(
            String currentAmount, Set<String> memberships, Instant calculationAt) {
        var offer = new Offer(
                "O-1", "SKU-1", "SHOP-1",
                Money.cny("3099.00"), Money.cny("2999.00"), Money.cny("0.00"),
                Instant.parse("2026-08-27T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"),
                "commerce-demo-2026.08.1", 0);
        return new PromotionApplicationContext(
                offer, "PRODUCT-1", "PHONE", Money.cny(currentAmount), memberships, calculationAt);
    }

    private String rule(String type, String body) {
        return """
                {
                  "promotionId": "PROMO-1",
                  "type": "%s",
                  %s
                }
                """.formatted(type, body);
    }
}
