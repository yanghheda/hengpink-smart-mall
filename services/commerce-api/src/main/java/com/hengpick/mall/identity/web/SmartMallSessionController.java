package com.hengpick.mall.identity.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.identity.application.CreatedSmartMallTicket;
import com.hengpick.mall.identity.application.H5Session;
import com.hengpick.mall.identity.application.SmartMallTicketService;
import com.hengpick.mall.identity.infrastructure.JwtRnAccessTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/smart-mall")
@Profile("database")
public class SmartMallSessionController {
    private final SmartMallTicketService ticketService;
    private final JwtRnAccessTokenVerifier accessTokenVerifier;
    private final Clock clock;

    public SmartMallSessionController(SmartMallTicketService ticketService,
            JwtRnAccessTokenVerifier accessTokenVerifier, Clock clock) {
        this.ticketService = ticketService;
        this.accessTokenVerifier = accessTokenVerifier;
        this.clock = clock;
    }

    @PostMapping("/tickets")
    ApiEnvelope<TicketResponse> create(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateTicketRequest request) {
        var user = accessTokenVerifier.verify(authorization);
        return ApiEnvelope.success(TicketResponse.from(ticketService.create(user.userId(), user.role(),
                request.hostType(), request.deviceSessionId(), request.h5Origin())), clock.instant());
    }

    @PostMapping("/sessions/exchange")
    ApiEnvelope<H5SessionResponse> exchange(@RequestHeader("Origin") String origin,
            @Valid @RequestBody ExchangeTicketRequest request) {
        return ApiEnvelope.success(H5SessionResponse.from(ticketService.exchange(request.ticket(), request.hostType(),
                request.deviceSessionId(), origin, request.bridgeVersion())), clock.instant());
    }

    public record CreateTicketRequest(
            @Pattern(regexp = "REACT_NATIVE") String hostType,
            @NotBlank @Size(max = 128) String deviceSessionId,
            @Pattern(regexp = "https://[^\\s]+|http://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?") @Size(max = 255) String h5Origin) {}

    public record ExchangeTicketRequest(
            @NotBlank @Size(max = 512) String ticket,
            @Pattern(regexp = "REACT_NATIVE") String hostType,
            @NotBlank @Size(max = 128) String deviceSessionId,
            @Pattern(regexp = "1\\.0") String bridgeVersion) {}

    public record TicketResponse(String ticket, String expiresAt) {
        static TicketResponse from(CreatedSmartMallTicket ticket) {
            return new TicketResponse(ticket.ticket(), ticket.expiresAt().toString());
        }
    }

    public record H5SessionResponse(String tokenType, String accessToken, String accessTokenExpiresAt,
            UserContext userContext, HostContext hostContext) {
        static H5SessionResponse from(H5Session session) {
            return new H5SessionResponse(session.tokenType(), session.accessToken(),
                    session.accessTokenExpiresAt().toString(), new UserContext(session.userId(), session.role()),
                    new HostContext(session.hostType(), session.deviceSessionId()));
        }
    }

    public record UserContext(String userId, String role) {}

    public record HostContext(String hostType, String deviceSessionId) {}
}
