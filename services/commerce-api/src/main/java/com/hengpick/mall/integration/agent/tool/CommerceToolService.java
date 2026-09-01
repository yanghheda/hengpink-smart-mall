package com.hengpick.mall.integration.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.catalog.application.CatalogQueryService;
import com.hengpick.mall.catalog.application.CatalogSearchService;
import com.hengpick.mall.catalog.domain.AttributeConstraint;
import com.hengpick.mall.catalog.domain.CatalogSearchCriteria;
import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.recommendation.domain.ConfidenceInput;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.DimensionScore;
import com.hengpick.mall.recommendation.domain.RecommendationCandidate;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import com.hengpick.mall.recommendation.domain.ScoreCard;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将既有 Catalog、Pricing 和 Recommendation 领域能力编排成内部 Tool。 */
public final class CommerceToolService {
    private final CatalogSearchService catalogSearchService;
    private final CatalogQueryService catalogQueryService;
    private final OfferQueryService offerQueryService;
    private final RecommendationScorer recommendationScorer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, CachedCall> calls = new ConcurrentHashMap<>();

    public CommerceToolService(
            CatalogSearchService catalogSearchService,
            CatalogQueryService catalogQueryService,
            OfferQueryService offerQueryService,
            RecommendationScorer recommendationScorer,
            ObjectMapper objectMapper,
            Clock clock) {
        this.catalogSearchService = catalogSearchService;
        this.catalogQueryService = catalogQueryService;
        this.offerQueryService = offerQueryService;
        this.recommendationScorer = recommendationScorer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ToolResponseEnvelope execute(String toolName, ToolRequestEnvelope request) {
        validateEnvelope(request);
        var requestHash = requestHash(toolName, request);
        var cached = calls.get(request.toolCallId());
        if (cached != null) {
            if (!cached.requestHash().equals(requestHash)) {
                throw new IllegalStateException("同一 toolCallId 的请求内容不一致");
            }
            return cached.response();
        }
        var data = switch (toolName) {
            case "search-products" -> searchProducts(request.input());
            case "get-product-specs" -> getProductSpecs(request.input());
            case "get-price-offers" -> getPriceOffers(request.input(), request.datasetVersion());
            case "calculate-final-price" -> calculateFinalPrice(request.input(), request.datasetVersion());
            case "score-candidates" -> scoreCandidates(request.input());
            default -> throw new IllegalArgumentException("Tool 不在固定 Registry 中");
        };
        var response = ToolResponseEnvelope.success(data, request.datasetVersion(), clock.instant());
        var raced = calls.putIfAbsent(request.toolCallId(), new CachedCall(requestHash, response));
        if (raced != null && !raced.requestHash().equals(requestHash)) {
            throw new IllegalStateException("同一 toolCallId 的并发请求内容不一致");
        }
        return raced == null ? response : raced.response();
    }

    private Object searchProducts(JsonNode input) {
        var budget = input.path("budget");
        var constraints = new ArrayList<AttributeConstraint>();
        for (var item : input.path("hardConstraints")) {
            constraints.add(new AttributeConstraint(
                    requiredTextAlias(item, "field", "name"), requiredText(item, "operator"), scalar(item.get("value"))));
        }
        var categoryId = requiredText(input, "categoryId");
        var minPrice = decimalOrNull(budget.get("min"));
        var maxPrice = decimalOrNull(budget.get("max"));
        com.hengpick.mall.catalog.domain.CatalogSearchResult result;
        var ignoredConstraints = List.<Map<String, Object>>of();
        try {
            result = catalogSearchService.search(new CatalogSearchCriteria(
                    categoryId, minPrice, maxPrice, true, constraints));
        } catch (IllegalArgumentException exception) {
            if (!"属性或操作符不受当前类目 Schema 支持".equals(exception.getMessage())) throw exception;
            ignoredConstraints = constraints.stream().map(constraint -> Map.<String, Object>of(
                    "field", constraint.attribute(), "operator", constraint.operator(),
                    "value", constraint.value() == null ? "" : constraint.value())).toList();
            result = catalogSearchService.search(new CatalogSearchCriteria(
                    categoryId, minPrice, maxPrice, true, List.of()));
        }
        var matched = result.matched().stream().map(candidate -> Map.of(
                "productId", candidate.productId(), "skuId", candidate.skuId(),
                "displayName", candidate.displayName(), "price", candidate.price().toPlainString()))
                .toList();
        var rejected = result.rejected().stream().map(item -> Map.of(
                "productId", item.candidate().productId(), "skuId", item.candidate().skuId(),
                "reasonCodes", item.reasonCodes())).toList();
        return Map.of("matchedCandidates", matched, "rejectedCandidates", rejected,
                "ignoredHardConstraints", ignoredConstraints);
    }

    private Object getProductSpecs(JsonNode input) {
        var candidates = new ArrayList<Map<String, Object>>();
        for (var item : input.path("candidates")) {
            var productId = requiredText(item, "productId");
            var skuId = requiredText(item, "skuId");
            var detail = catalogQueryService.getProduct(productId, skuId);
            var sku = detail.selectedSku();
            if (sku == null) throw new IllegalArgumentException("商品规格未命中请求 SKU");
            var candidate = new LinkedHashMap<String, Object>();
            candidate.put("productId", productId);
            candidate.put("skuId", skuId);
            candidate.put("displayName", detail.displayName() + " " + sku.displayName());
            candidate.put("attributes", sku.attributes());
            candidate.put("facts", catalogQueryService.getFacts(productId, skuId));
            candidates.add(candidate);
        }
        return Map.of("candidates", candidates);
    }

    private Object getPriceOffers(JsonNode input, String datasetVersion) {
        var offers = new ArrayList<Map<String, Object>>();
        for (var skuIdNode : input.path("skuIds")) {
            var skuId = skuIdNode.asText();
            var query = offerQueryService.findValidOffers(skuId);
            for (var offer : query.offers()) {
                if (!datasetVersion.equals(offer.datasetVersion())) {
                    throw new IllegalArgumentException("报价数据版本与 Run 不一致");
                }
                offers.add(Map.of(
                        "offerId", offer.offerId(), "skuId", offer.skuId(),
                        "salePrice", offer.salePrice().amount().toPlainString(),
                        "currency", offer.currency()));
            }
        }
        return Map.of("offers", offers);
    }

    private Object calculateFinalPrice(JsonNode input, String datasetVersion) {
        var plans = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var requested : input.path("offers")) {
            var skuId = requiredText(requested, "skuId");
            var offerId = requiredText(requested, "offerId");
            var offer = offerQueryService.findValidOffers(skuId).offers().stream()
                    .filter(item -> item.offerId().equals(offerId))
                    .filter(item -> item.datasetVersion().equals(datasetVersion))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("报价不存在或版本不匹配"));
            var finalPrice = offer.salePrice().amount().add(offer.additionalFee().amount());
            plans.put(skuId, List.of(Map.of(
                    "pricePlanId", offer.offerId() + ":BASE", "offerId", offer.offerId(),
                    "type", "LOWEST_PRICE", "finalPrice", finalPrice.toPlainString(),
                    "currency", offer.currency(), "appliedPromotions", List.of())));
        }
        return Map.of("pricePlans", plans);
    }

    private Object scoreCandidates(JsonNode input) {
        var candidates = new ArrayList<RecommendationCandidate>();
        for (var item : input.path("candidates")) {
            var skuId = requiredText(item, "skuId");
            var plan = input.path("pricePlans").path(skuId).path(0);
            var price = new BigDecimal(requiredText(plan, "finalPrice"));
            var dimensions = new EnumMap<Dimension, DimensionScore>(Dimension.class);
            dimensions.put(Dimension.NEED_MATCH, dimension(Dimension.NEED_MATCH, item, 86));
            dimensions.put(Dimension.PRICE_VALUE, dimension(Dimension.PRICE_VALUE, item, priceScore(price)));
            dimensions.put(Dimension.REVIEW_QUALITY, dimension(Dimension.REVIEW_QUALITY, item, 70));
            dimensions.put(Dimension.PROMOTION_VALUE, dimension(Dimension.PROMOTION_VALUE, item, 60));
            dimensions.put(Dimension.RELIABILITY, dimension(Dimension.RELIABILITY, item, 80));
            var raw = new ScoreCard(skuId, List.of(), dimensions, "scoring-v1");
            candidates.add(new RecommendationCandidate(
                    raw, new ConfidenceInput("0.90", "0.50", "1.00", "1.00", "0.00", false),
                    List.of(), price, List.of()));
        }
        var weights = new EnumMap<Dimension, BigDecimal>(Dimension.class);
        for (var dimension : Dimension.values()) weights.put(dimension, BigDecimal.ZERO);
        var result = recommendationScorer.score(candidates, weights);
        var cards = result.ranked().stream().limit(3).map(item -> Map.of(
                "skuId", item.scoreCard().skuId(),
                "finalScore", item.scoreCard().finalScore().toPlainString(),
                "finalPrice", item.finalPrice().toPlainString(),
                "confidence", item.scoreCard().confidence().score().toPlainString(),
                "scoreCard", item.scoreCard())).toList();
        return Map.of("scoreCards", cards);
    }

    private DimensionScore dimension(Dimension dimension, JsonNode item, int score) {
        return new DimensionScore(
                dimension, BigDecimal.valueOf(score), BigDecimal.ONE,
                List.of("FACT-" + requiredText(item, "skuId") + "-" + dimension.name()), List.of());
    }

    private int priceScore(BigDecimal price) {
        return BigDecimal.valueOf(100).subtract(price.divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO).intValue();
    }

    private void validateEnvelope(ToolRequestEnvelope request) {
        if (request.runId() == null || request.runId().isBlank()
                || request.toolCallId() == null || request.toolCallId().isBlank()
                || request.datasetVersion() == null || request.datasetVersion().isBlank()
                || request.runVersion() < 1 || request.timeoutMs() < 100 || request.timeoutMs() > 5000
                || request.input() == null) {
            throw new IllegalArgumentException("Tool 请求信封不合法");
        }
    }

    private String requestHash(String toolName, ToolRequestEnvelope request) {
        try {
            var bytes = objectMapper.writeValueAsBytes(Map.of("tool", toolName, "request", request));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Tool 请求无法计算幂等哈希", exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.asText();
    }

    private String requiredTextAlias(JsonNode node, String field, String alias) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) value = node.get(alias);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.asText();
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : new BigDecimal(node.asText());
    }

    private Object scalar(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        return node.asText();
    }

    private record CachedCall(String requestHash, ToolResponseEnvelope response) {}
}
