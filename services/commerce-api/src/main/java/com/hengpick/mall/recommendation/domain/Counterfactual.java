package com.hengpick.mall.recommendation.domain;

import java.util.Objects;

public record Counterfactual(
        CounterfactualType type,
        Dimension changedDimension,
        AdjustmentContext beforeContext,
        AdjustmentContext afterContext,
        String beforeSkuId,
        String afterSkuId,
        ScoreCard beforeScoreCard,
        ScoreCard afterScoreCard) {

    public Counterfactual {
        Objects.requireNonNull(type, "反事实类型不能为空");
        Objects.requireNonNull(beforeContext, "变更前条件不能为空");
        Objects.requireNonNull(afterContext, "变更后条件不能为空");
        Objects.requireNonNull(beforeSkuId, "变更前首选不能为空");
        Objects.requireNonNull(afterSkuId, "变更后首选不能为空");
        Objects.requireNonNull(beforeScoreCard, "变更前评分卡不能为空");
        Objects.requireNonNull(afterScoreCard, "变更后评分卡不能为空");
        if (beforeSkuId.equals(afterSkuId)) throw new IllegalArgumentException("反事实必须改变首选商品");
        if (type == CounterfactualType.WEIGHT && changedDimension == null) {
            throw new IllegalArgumentException("权重反事实必须记录变化维度");
        }
    }
}
