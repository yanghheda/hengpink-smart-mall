package com.hengpick.mall.catalog.web;

import com.hengpick.mall.catalog.application.CatalogQueryService;
import com.hengpick.mall.catalog.application.CatalogSearchService;
import com.hengpick.mall.catalog.domain.AttributeConstraint;
import com.hengpick.mall.catalog.domain.CatalogSearchCriteria;
import com.hengpick.mall.catalog.domain.CatalogSearchResult;
import com.hengpick.mall.catalog.domain.CatalogFact;
import com.hengpick.mall.catalog.domain.ProductDetail;
import com.hengpick.mall.catalog.domain.ProductPage;
import java.time.Clock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("database")
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog", description = "商品目录查询接口")
public class CatalogController {
    private final CatalogQueryService queryService;
    private final Clock clock;
    private final CatalogSearchService searchService;

    public CatalogController(CatalogQueryService queryService, CatalogSearchService searchService, Clock clock) {
        this.queryService = queryService;
        this.searchService = searchService;
        this.clock = clock;
    }

    @PostMapping("/search")
    @Operation(summary = "结构化搜索商品", description = "按 SKU 执行预算、库存和属性硬条件，并返回淘汰原因。")
    public ApiEnvelope<CatalogSearchResult> search(@RequestBody SearchRequest request) {
        var criteria = new CatalogSearchCriteria(request.categoryId(), request.minPrice(), request.maxPrice(),
                request.inStockOnly(), request.attributes());
        return ApiEnvelope.success(searchService.search(criteria), clock.instant());
    }

    @GetMapping
    @Operation(summary = "查询商品列表", description = "仅返回有效商品，并按创建时间倒序分页。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "分页参数不合法")
    })
    public ApiEnvelope<ProductPageResponse> listProducts(
            @Parameter(description = "从零开始的页码", example = "0", in = ParameterIn.QUERY)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，范围为 1 至 100", example = "20", in = ParameterIn.QUERY)
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listProducts(page, size);
        return ApiEnvelope.success(ProductPageResponse.from(result), clock.instant());
    }

    @GetMapping("/{productId}")
    @Operation(summary = "查询商品详情", description = "指定 skuId 时，该 SKU 必须属于当前商品。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "商品不存在或 SKU 不属于该商品"),
            @ApiResponse(responseCode = "400", description = "请求参数不合法")
    })
    public ApiEnvelope<ProductDetail> getProduct(
            @Parameter(description = "商品唯一标识", example = "01JPROD000000000000000001", in = ParameterIn.PATH)
            @PathVariable String productId,
            @Parameter(description = "可选的 SKU 唯一标识", example = "01JSKU00000000000000000001", in = ParameterIn.QUERY)
            @RequestParam(required = false) String skuId) {
        return ApiEnvelope.success(queryService.getProduct(productId, skuId), clock.instant());
    }

    @GetMapping("/{productId}/facts")
    @Operation(summary = "查询商品事实", description = "返回 Product 共享事实与目标 SKU 专属事实，Fact ID 可稳定复算。")
    public ApiEnvelope<java.util.List<CatalogFact>> facts(
            @PathVariable String productId,
            @RequestParam(required = false) String skuId) {
        return ApiEnvelope.success(queryService.getFacts(productId, skuId), clock.instant());
    }

    /** 商品列表 API 的分页响应载体。 */
    public record ProductPageResponse(
            @Schema(description = "当前页商品列表") java.util.List<com.hengpick.mall.catalog.domain.ProductSummary> items,
            @Schema(description = "从零开始的页码", example = "0") int page,
            @Schema(description = "每页条数", example = "20") int size,
            @Schema(description = "符合条件的商品总数", example = "6") long totalElements,
            @Schema(description = "总页数", example = "1") int totalPages) {
        static ProductPageResponse from(ProductPage page) {
            return new ProductPageResponse(page.items(), page.page(), page.size(), page.totalElements(), page.totalPages());
        }
    }

    public record SearchRequest(String categoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                boolean inStockOnly, java.util.List<AttributeConstraint> attributes) {}
}
