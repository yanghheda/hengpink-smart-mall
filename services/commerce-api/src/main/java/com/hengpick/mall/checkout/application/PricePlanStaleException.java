package com.hengpick.mall.checkout.application;

/** 报告价格方案与当前服务端事实不一致。 */
public final class PricePlanStaleException extends RuntimeException {
    public PricePlanStaleException() { super("价格方案已变化，请刷新报告后重试"); }
}
