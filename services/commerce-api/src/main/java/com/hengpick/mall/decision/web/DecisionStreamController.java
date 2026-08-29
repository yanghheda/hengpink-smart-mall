package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.decision.application.DecisionSessionQueryService;
import com.hengpick.mall.decision.application.DecisionStreamQueryService;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 向拥有 Session 的 H5 客户端提供可携带认证头的 SSE 流。 */
@RestController
@Profile("database")
@RequestMapping("/api/v1/decision-sessions/{sessionId}")
public class DecisionStreamController {
    private final DecisionStreamQueryService queryService;
    private final DecisionSseService sseService;
    private final DecisionSessionQueryService sessionQueryService;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public DecisionStreamController(
            DecisionStreamQueryService queryService,
            DecisionSseService sseService,
            DecisionSessionQueryService sessionQueryService,
            JwtH5AccessTokenVerifier tokenVerifier,
            Clock clock) {
        this.queryService = queryService;
        this.sseService = sseService;
        this.sessionQueryService = sessionQueryService;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @GetMapping
    public ApiEnvelope<com.hengpick.mall.decision.domain.DecisionSessionSnapshot> snapshot(
            @PathVariable String sessionId,
            @RequestHeader("Authorization") String authorization) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(sessionQueryService.requireSnapshot(sessionId, subject.userId()), clock.instant());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String sessionId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        var subject = tokenVerifier.verify(authorization);
        var runId = queryService.requireCurrentRun(sessionId, subject.userId());
        return sseService.open(runId, lastEventId);
    }
}
