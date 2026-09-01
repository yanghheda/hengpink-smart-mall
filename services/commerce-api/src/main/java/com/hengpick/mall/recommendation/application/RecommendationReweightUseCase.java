package com.hengpick.mall.recommendation.application;

import com.hengpick.mall.recommendation.domain.Dimension;
import java.math.BigDecimal;
import java.util.Map;

public interface RecommendationReweightUseCase {
    ReweightResult reweight(
            String userId,
            String sessionId,
            int expectedReportVersion,
            Map<Dimension, BigDecimal> requestedWeights);
}
