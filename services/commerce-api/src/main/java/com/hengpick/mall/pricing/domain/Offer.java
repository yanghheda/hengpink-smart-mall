package com.hengpick.mall.pricing.domain;

import java.time.Instant;
import java.util.Objects;

/** SKU 在指定店铺中的带有效期报价。 */
public record Offer(
        /* 报价唯一标识。 */
        String offerId,
        /* 报价绑定的 SKU 标识。 */
        String skuId,
        /* 报价所属店铺标识。 */
        String shopId,
        /* 商品标价。 */
        Money listPrice,
        /* 当前销售价。 */
        Money salePrice,
        /* 购买时必须计入的附加费用。 */
        Money additionalFee,
        /* 报价开始生效的 UTC 时刻，包含该时刻。 */
        Instant validFrom,
        /* 报价结束生效的 UTC 时刻，不包含该时刻。 */
        Instant validTo,
        /* 报价所属的数据集版本。 */
        String datasetVersion,
        /* 报价的乐观锁版本。 */
        long version) {

    public Offer {
        Objects.requireNonNull(offerId, "报价标识不能为空");
        Objects.requireNonNull(skuId, "SKU 标识不能为空");
        Objects.requireNonNull(shopId, "店铺标识不能为空");
        Objects.requireNonNull(listPrice, "标价不能为空");
        Objects.requireNonNull(salePrice, "销售价不能为空");
        Objects.requireNonNull(additionalFee, "附加费用不能为空");
        Objects.requireNonNull(validFrom, "有效期开始时间不能为空");
        Objects.requireNonNull(validTo, "有效期结束时间不能为空");
        Objects.requireNonNull(datasetVersion, "数据集版本不能为空");
        if (!validFrom.isBefore(validTo)) {
            throw new IllegalArgumentException("报价有效期开始时间必须早于结束时间");
        }
        if (!listPrice.currency().equals(salePrice.currency())
                || !listPrice.currency().equals(additionalFee.currency())) {
            throw new IllegalArgumentException("同一报价中的金额币种必须一致");
        }
    }

    public boolean isValidAt(Instant calculationAt) {
        Objects.requireNonNull(calculationAt, "计算时刻不能为空");
        return !calculationAt.isBefore(validFrom) && calculationAt.isBefore(validTo);
    }

    public String currency() {
        return salePrice.currency();
    }
}
