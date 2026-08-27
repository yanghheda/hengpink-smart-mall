package com.hengpick.mall.pricing.web;

import com.hengpick.mall.pricing.application.OfferQueryResult;
import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.pricing.domain.Offer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向商城客户端的 SKU 报价查询入口。 */
@RestController
@Profile("database")
@RequestMapping("/api/v1/skus")
@Tag(name = "Pricing", description = "确定性报价查询接口")
public class PricingController {
    private final OfferQueryService queryService;

    public PricingController(OfferQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{skuId}/offers")
    @Operation(summary = "查询 SKU 有效报价", description = "按服务端固定计算时刻查询半开有效区间内的报价。")
    public PricingEnvelope<OfferListView> getOffers(@PathVariable String skuId) {
        var result = queryService.findValidOffers(skuId);
        return PricingEnvelope.success(OfferListView.from(result), result.calculationAt());
    }

    /** 有效报价列表的对外响应。 */
    public record OfferListView(
            /* 被查询的 SKU 标识。 */
            String skuId,
            /* 本次查询使用的固定计算时刻。 */
            String calculationAt,
            /* 有效报价列表。 */
            List<OfferView> offers) {
        static OfferListView from(OfferQueryResult result) {
            return new OfferListView(
                    result.skuId(),
                    result.calculationAt().toString(),
                    result.offers().stream().map(OfferView::from).toList());
        }
    }

    /** 单条有效报价的对外响应。 */
    public record OfferView(
            /* 报价唯一标识。 */
            String offerId,
            /* 报价所属店铺标识。 */
            String shopId,
            /* 两位小数字符串格式的商品标价。 */
            String listPrice,
            /* 两位小数字符串格式的当前销售价。 */
            String salePrice,
            /* 两位小数字符串格式的必要附加费用。 */
            String additionalFee,
            /* ISO 4217 币种代码。 */
            String currency,
            /* 包含边界的生效时刻。 */
            String validFrom,
            /* 不包含边界的失效时刻。 */
            String validTo,
            /* 数据集版本。 */
            String datasetVersion,
            /* 报价版本。 */
            long version) {
        static OfferView from(Offer offer) {
            return new OfferView(
                    offer.offerId(),
                    offer.shopId(),
                    offer.listPrice().toString(),
                    offer.salePrice().toString(),
                    offer.additionalFee().toString(),
                    offer.currency(),
                    offer.validFrom().toString(),
                    offer.validTo().toString(),
                    offer.datasetVersion(),
                    offer.version());
        }
    }

    /** Pricing API 的成功响应信封。 */
    public record PricingEnvelope<T>(
            /* 服务端生成的请求标识。 */
            String requestId,
            /* 业务响应数据。 */
            T data,
            /* 响应元数据。 */
            Meta meta) {
        static <T> PricingEnvelope<T> success(T data, Instant serverTime) {
            return new PricingEnvelope<>(UUID.randomUUID().toString(), data, new Meta(serverTime.toString()));
        }
    }

    /** Pricing API 的响应元数据。 */
    public record Meta(
            /* 服务端响应使用的 UTC 时刻。 */
            String serverTime) {}
}
