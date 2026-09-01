package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.decision.application.DecisionRunCoordinator;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 接收 H5 的原始购买需求，并启动由服务端控制的首次分析。 */
@RestController
@Profile("database")
@RequestMapping("/api/v1/decision-sessions")
public class DecisionSubmissionController {
    private final DecisionRunCoordinator coordinator;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public DecisionSubmissionController(
            DecisionRunCoordinator coordinator, JwtH5AccessTokenVerifier tokenVerifier, Clock clock) {
        this.coordinator = coordinator;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @PostMapping
    public ApiEnvelope<StartedResponse> start(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody StartRequest request) {
        var subject = tokenVerifier.verify(authorization);
        var started = coordinator.startInitialRun(subject.userId(), request.requirement().trim());
        return ApiEnvelope.success(new StartedResponse(started.session().id(), started.run().id(),
                started.run().runVersion(), started.session().status().name()), clock.instant());
    }

    @PostMapping("/{sessionId}/messages")
    public ApiEnvelope<StartedResponse> reply(
            @PathVariable String sessionId, @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ReplyRequest request) {
        var subject = tokenVerifier.verify(authorization);
        var started = coordinator.continueAfterClarification(subject.userId(), sessionId, request.content().trim());
        return ApiEnvelope.success(new StartedResponse(started.session().id(), started.run().id(),
                started.run().runVersion(), started.session().status().name()), clock.instant());
    }

    public record StartRequest(@NotBlank @Size(max = 2000) String requirement) {}
    public record ReplyRequest(@NotBlank @Size(max = 2000) String content) {}

    public record StartedResponse(String sessionId, String runId, int runVersion, String status) {}
}
