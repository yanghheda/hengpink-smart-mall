package com.hengpick.mall.checkout.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.checkout.application.PurchaseIntentService;
import com.hengpick.mall.checkout.domain.PurchaseIntent;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.identity.infrastructure.JwtRnAccessTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/purchase-intents")
public class CheckoutController {
    private final PurchaseIntentService service;
    private final JwtH5AccessTokenVerifier h5Verifier;
    private final JwtRnAccessTokenVerifier rnVerifier;
    private final Clock clock;

    public CheckoutController(PurchaseIntentService service, JwtH5AccessTokenVerifier h5Verifier,
            JwtRnAccessTokenVerifier rnVerifier, Clock clock) {
        this.service = service;
        this.h5Verifier = h5Verifier;
        this.rnVerifier = rnVerifier;
        this.clock = clock;
    }

    @PostMapping
    public ApiEnvelope<PurchaseIntent> create(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateRequest request) {
        var subject = h5Verifier.verify(authorization);
        return ApiEnvelope.success(service.create(subject.userId(), request.sessionId(), request.reportVersion(),
                request.skuId(), request.pricePlanId(), idempotencyKey), clock.instant());
    }

    @GetMapping("/{id}")
    public ApiEnvelope<PurchaseIntent> get(@PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        var subject = rnVerifier.verify(authorization);
        return ApiEnvelope.success(service.get(subject.userId(), id), clock.instant());
    }

    @PostMapping("/{id}/confirm")
    public ApiEnvelope<PurchaseIntent> confirm(@PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        var subject = rnVerifier.verify(authorization);
        return ApiEnvelope.success(service.confirm(subject.userId(), id), clock.instant());
    }

    public record CreateRequest(@NotBlank String sessionId, @Positive int reportVersion,
            @NotBlank String skuId, @NotBlank String pricePlanId) {}
}
