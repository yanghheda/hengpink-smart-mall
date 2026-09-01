package com.hengpick.mall.recommendation.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.recommendation.application.RecommendationReweightUseCase;
import com.hengpick.mall.recommendation.application.ReweightResult;
import com.hengpick.mall.recommendation.domain.Dimension;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/decision-sessions")
public class RecommendationController {
    private final RecommendationReweightUseCase service;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public RecommendationController(
            RecommendationReweightUseCase service,
            JwtH5AccessTokenVerifier tokenVerifier,
            Clock clock) {
        this.service = service;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @PutMapping("/{sessionId}/weights")
    public ApiEnvelope<ReweightResult> reweight(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReweightRequest request) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.reweight(subject.userId(), sessionId,
                request.reportVersion(), request.weights()), clock.instant());
    }

    public record ReweightRequest(
            @Positive int reportVersion,
            @NotNull Map<Dimension, BigDecimal> weights) {}
}
