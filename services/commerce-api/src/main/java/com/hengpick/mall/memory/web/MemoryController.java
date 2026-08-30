package com.hengpick.mall.memory.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.memory.application.MemoryDecisionResult;
import com.hengpick.mall.memory.application.MemoryService;
import com.hengpick.mall.memory.domain.MemoryDecision;
import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.MemoryScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1")
public class MemoryController {
    private final MemoryService service;
    private final JwtH5AccessTokenVerifier tokenVerifier;
    private final Clock clock;

    public MemoryController(MemoryService service, JwtH5AccessTokenVerifier tokenVerifier, Clock clock) {
        this.service = service;
        this.tokenVerifier = tokenVerifier;
        this.clock = clock;
    }

    @PostMapping("/decision-sessions/{sessionId}/memory-proposals")
    public ApiEnvelope<MemoryProposal> propose(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProposalRequest request) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.propose(subject.userId(), sessionId, request.proposalType(),
                request.preferenceKey(), request.value(), request.rationaleSummary(), request.scope()), clock.instant());
    }

    @PostMapping("/me/memory-proposals/{proposalId}/decision")
    public ApiEnvelope<MemoryDecisionResult> decide(
            @PathVariable String proposalId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DecisionRequest request) {
        var subject = tokenVerifier.verify(authorization);
        return ApiEnvelope.success(service.decide(subject.userId(), proposalId, request.decision(), request.value()),
                clock.instant());
    }

    public record ProposalRequest(
            @NotBlank @Size(max = 64) String proposalType,
            @NotBlank @Size(max = 128) String preferenceKey,
            @NotEmpty Map<String, Object> value,
            @NotBlank @Size(max = 500) String rationaleSummary,
            MemoryScope scope) {}

    public record DecisionRequest(@NotNull MemoryDecision decision, Map<String, Object> value) {}
}
