package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.decision.application.DecisionTraceService;
import com.hengpick.mall.decision.domain.DecisionTraceSnapshot;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供 DEMO_ADMIN 使用的单 Run 脱敏 Trace。 */
@RestController
@Profile("database")
@RequestMapping("/api/v1/admin/decision-runs")
public class DecisionTraceController {
    private final DecisionTraceService service;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public DecisionTraceController(DecisionTraceService service, JwtH5AccessTokenVerifier tokenVerifier, Clock clock) {
        this.service = service;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @GetMapping("/{runId}/trace")
    public ApiEnvelope<DecisionTraceSnapshot> getTrace(@PathVariable String runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiEnvelope.success(service.getTrace(tokenVerifier.verify(authorization), runId), clock.instant());
    }
}
