package com.hengpick.mall.checkout.domain;

/** 重新读取并计算当前价格方案的端口。 */
@FunctionalInterface
public interface CurrentPricePlanPort {
    CurrentPricePlan revalidate(String skuId, String pricePlanId);
}
