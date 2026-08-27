package com.hengpick.mall.pricing.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** MyBatis 查询返回的报价行模型。 */
public record OfferRow(
        /* 报价唯一标识。 */
        String offerId,
        /* 报价绑定的 SKU 标识。 */
        String skuId,
        /* 报价所属店铺标识。 */
        String shopId,
        /* 商品标价。 */
        BigDecimal listPrice,
        /* 当前销售价。 */
        BigDecimal salePrice,
        /* 必要附加费用。 */
        BigDecimal additionalFee,
        /* ISO 4217 币种代码。 */
        String currency,
        /* 报价开始生效的 UTC 时刻。 */
        LocalDateTime validFrom,
        /* 报价结束生效的 UTC 时刻。 */
        LocalDateTime validTo,
        /* 数据集版本。 */
        String datasetVersion,
        /* 乐观锁版本。 */
        long version) {}
