package com.hengpick.mall.pricing.domain.promotion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.pricing.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 将 Promotion DSL 编译为经过完整校验的领域规则对象。 */
public final class PromotionCompiler {
    private final ObjectMapper objectMapper;

    public PromotionCompiler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 解析器不能为空");
    }

    public CompiledPromotion compile(String ruleJson) {
        try {
            return compile(objectMapper.readTree(ruleJson));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("优惠规则不是合法 JSON", exception);
        }
    }

    private CompiledPromotion compile(JsonNode root) {
        var promotionId = requiredText(root, "promotionId");
        var type = parseType(requiredText(root, "type"));
        var scope = root.path("scope");
        var condition = root.path("condition");
        var benefit = root.path("benefit");

        var amountOffText = optionalText(benefit, "amountOff");
        var discountRateText = optionalText(benefit, "discountRate");
        if ((amountOffText == null) == (discountRateText == null)) {
            throw new IllegalArgumentException("每条优惠只能配置一种优惠权益");
        }

        var amountOff = amountOffText == null ? null : Money.cny(amountOffText);
        var discountRate = discountRateText == null ? null : decimal(discountRateText, "discountRate");
        if (discountRate != null
                && (discountRate.compareTo(BigDecimal.ZERO) <= 0 || discountRate.compareTo(BigDecimal.ONE) >= 0)) {
            throw new IllegalArgumentException("discountRate 必须大于 0 且小于 1");
        }
        if ((type == PromotionType.DIRECT_REDUCTION || type == PromotionType.FULL_REDUCTION)
                && amountOff == null) {
            throw new IllegalArgumentException(type + " 只支持 amountOff");
        }

        var minAmountText = optionalText(condition, "minAmount");
        var minAmount = minAmountText == null ? null : Money.cny(minAmountText);
        if (type == PromotionType.FULL_REDUCTION && minAmount == null) {
            throw new IllegalArgumentException("FULL_REDUCTION 必须配置 minAmount");
        }
        var membership = optionalText(condition, "membership");
        if (type == PromotionType.MEMBER_DISCOUNT && membership == null) {
            throw new IllegalArgumentException("MEMBER_DISCOUNT 必须配置 membership");
        }

        var timeWindow = condition.path("timeWindow");
        var startAt = optionalInstant(timeWindow, "start");
        var endAt = optionalInstant(timeWindow, "end");
        if ((startAt == null) != (endAt == null)) {
            throw new IllegalArgumentException("timeWindow 必须同时配置 start 和 end");
        }
        if (startAt != null && !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("优惠开始时间必须早于结束时间");
        }

        return new CompiledPromotion(
                promotionId,
                type,
                stringSet(scope, "categoryIds"),
                stringSet(scope, "productIds"),
                stringSet(scope, "skuIds"),
                minAmount,
                membership,
                startAt,
                endAt,
                amountOff,
                discountRate,
                promotionTypes(root, "stackableWithTypes"),
                stringSet(root, "exclusiveWithIds"),
                optionalInteger(root, "priority", 0));
    }

    private PromotionType parseType(String value) {
        try {
            return PromotionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的优惠类型: " + value, exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        var value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        var value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private Set<String> stringSet(JsonNode node, String field) {
        var values = node.path(field);
        if (values.isMissingNode()) {
            return Set.of();
        }
        if (!values.isArray()) {
            throw new IllegalArgumentException(field + " 必须是字符串数组");
        }
        var result = new HashSet<String>();
        values.forEach(value -> {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalArgumentException(field + " 必须是非空字符串数组");
            }
            result.add(value.textValue());
        });
        return result;
    }

    private Set<PromotionType> promotionTypes(JsonNode node, String field) {
        var result = new HashSet<PromotionType>();
        for (var value : stringSet(node, field)) {
            result.add(parseType(value));
        }
        return result;
    }

    private int optionalInteger(JsonNode node, String field, int defaultValue) {
        var value = node.path(field);
        if (value.isMissingNode()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " 必须是 32 位整数");
        }
        return value.intValue();
    }

    private BigDecimal decimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " 必须是十进制字符串", exception);
        }
    }

    private Instant optionalInstant(JsonNode node, String field) {
        var value = optionalText(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " 必须是带时区的 ISO-8601 时刻", exception);
        }
    }
}
