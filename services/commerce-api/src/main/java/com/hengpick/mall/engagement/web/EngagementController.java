package com.hengpick.mall.engagement.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.engagement.application.EngagementService;
import com.hengpick.mall.engagement.domain.Favorite;
import com.hengpick.mall.engagement.domain.FavoriteType;
import com.hengpick.mall.engagement.domain.HistoricalReport;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1")
public class EngagementController {
    private final EngagementService service;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public EngagementController(EngagementService service, JwtH5AccessTokenVerifier tokenVerifier, Clock clock) {
        this.service = service;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @PostMapping("/favorites")
    public ApiEnvelope<Favorite> addFavorite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FavoriteRequest request) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.addFavorite(subject.userId(), request.entityType(), request.entityId(),
                request.reportVersion()), clock.instant());
    }

    @GetMapping("/favorites")
    public ApiEnvelope<List<Favorite>> listFavorites(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) FavoriteType entityType) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.listFavorites(subject.userId(), entityType), clock.instant());
    }

    @DeleteMapping("/favorites/{favoriteId}")
    public ApiEnvelope<Map<String, Boolean>> deleteFavorite(
            @PathVariable String favoriteId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var subject = tokenVerifier.verify(authorization);
        service.deleteFavorite(subject.userId(), favoriteId);
        return ApiEnvelope.success(Map.of("deleted", true), clock.instant());
    }

    @GetMapping("/decision-sessions/{sessionId}/reports/{version}")
    public ApiEnvelope<HistoricalReport> getHistoricalReport(
            @PathVariable String sessionId,
            @PathVariable @Positive int version,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.getHistoricalReport(subject.userId(), sessionId, version), clock.instant());
    }

    @DeleteMapping("/decision-sessions/{sessionId}/reports/{version}")
    public ApiEnvelope<Map<String, Boolean>> deleteHistoricalReport(
            @PathVariable String sessionId,
            @PathVariable @Positive int version,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var subject = tokenVerifier.verify(authorization);
        service.deleteHistoricalReport(subject.userId(), sessionId, version);
        return ApiEnvelope.success(Map.of("deleted", true), clock.instant());
    }

    public record FavoriteRequest(
            @NotNull FavoriteType entityType,
            @NotBlank @Size(max = 64) String entityId,
            @Positive Integer reportVersion) {}
}
