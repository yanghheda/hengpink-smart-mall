package com.hengpick.mall.decision.web;

import com.hengpick.mall.decision.application.DecisionStreamQueryService;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
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
    private final JwtH5AccessTokenVerifier tokenVerifier;

    public DecisionStreamController(
            DecisionStreamQueryService queryService,
            DecisionSseService sseService,
            JwtH5AccessTokenVerifier tokenVerifier) {
        this.queryService = queryService;
        this.sseService = sseService;
        this.tokenVerifier = tokenVerifier;
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
