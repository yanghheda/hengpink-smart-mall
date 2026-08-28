package com.hengpick.mall.identity.application;

import com.hengpick.mall.identity.domain.H5SessionTokenIssuer;
import com.hengpick.mall.identity.domain.RefreshTokenGenerator;
import com.hengpick.mall.identity.domain.SmartMallTicket;
import com.hengpick.mall.identity.domain.SmartMallTicketRepository;
import com.hengpick.mall.identity.domain.TokenDigester;
import java.time.Clock;
import java.time.Duration;

public class SmartMallTicketService {
    private final SmartMallTicketRepository repository;
    private final RefreshTokenGenerator ticketGenerator;
    private final TokenDigester tokenDigester;
    private final H5SessionTokenIssuer sessionTokenIssuer;
    private final Clock clock;
    private final Duration ticketTtl;
    private final Duration sessionTtl;

    public SmartMallTicketService(SmartMallTicketRepository repository, RefreshTokenGenerator ticketGenerator,
            TokenDigester tokenDigester, H5SessionTokenIssuer sessionTokenIssuer, Clock clock, Duration ticketTtl,
            Duration sessionTtl) {
        this.repository = repository;
        this.ticketGenerator = ticketGenerator;
        this.tokenDigester = tokenDigester;
        this.sessionTokenIssuer = sessionTokenIssuer;
        this.clock = clock;
        this.ticketTtl = ticketTtl;
        this.sessionTtl = sessionTtl;
    }

    public CreatedSmartMallTicket create(String userId, String role, String hostType, String deviceSessionId,
            String h5Origin) {
        var now = clock.instant();
        for (var attempt = 0; attempt < 3; attempt++) {
            var plainTicket = ticketGenerator.generate();
            var ticket = new SmartMallTicket(tokenDigester.digest(plainTicket), userId, role, hostType,
                    deviceSessionId, h5Origin, now.plus(ticketTtl));
            if (repository.save(ticket, ticketTtl)) {
                return new CreatedSmartMallTicket(plainTicket, ticket.expiresAt());
            }
        }
        throw new IllegalStateException("无法生成唯一的 Smart Mall Ticket");
    }

    public H5Session exchange(String plainTicket, String hostType, String deviceSessionId, String h5Origin,
            String bridgeVersion) {
        if (!"1.0".equals(bridgeVersion)) {
            throw new SmartMallTicketException("BRIDGE_VERSION_UNSUPPORTED", "Bridge 版本不受支持");
        }
        var now = clock.instant();
        var result = repository.consume(tokenDigester.digest(plainTicket), hostType, deviceSessionId, h5Origin, now);
        if (result.status() == SmartMallTicketRepository.Status.EXPIRED) {
            throw new SmartMallTicketException("SMART_TICKET_EXPIRED", "Smart Mall Ticket 已过期");
        }
        if (result.status() != SmartMallTicketRepository.Status.CONSUMED) {
            throw new SmartMallTicketException("SMART_TICKET_INVALID", "Smart Mall Ticket 无效或已兑换");
        }
        var expiresAt = now.plus(sessionTtl);
        var ticket = result.ticket();
        return new H5Session("Bearer", sessionTokenIssuer.issue(ticket.userId(), ticket.role(), now, expiresAt),
                expiresAt, ticket.userId(), ticket.role(), ticket.hostType(), ticket.deviceSessionId());
    }
}
