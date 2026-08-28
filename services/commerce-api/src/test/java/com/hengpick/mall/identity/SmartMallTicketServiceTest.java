package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.identity.application.SmartMallTicketException;
import com.hengpick.mall.identity.application.SmartMallTicketService;
import com.hengpick.mall.identity.domain.SmartMallTicket;
import com.hengpick.mall.identity.domain.SmartMallTicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmartMallTicketServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");
    private InMemoryTicketRepository repository;
    private SmartMallTicketService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTicketRepository();
        service = new SmartMallTicketService(repository, () -> "opaque-ticket", value -> "ticket-digest",
                (userId, role, issuedAt, expiresAt) -> "h5:" + userId, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5), Duration.ofMinutes(30));
    }

    @Test
    void createsOnlyHashedFiveMinuteTicketMetadata() {
        var created = service.create("USER-1", "DEMO_USER", "REACT_NATIVE", "device-1", "https://smart.example");

        assertThat(created.ticket()).isEqualTo("opaque-ticket");
        assertThat(created.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(repository.tickets).containsOnlyKeys("ticket-digest");
        assertThat(repository.tickets.values().iterator().next().ticketHash()).doesNotContain("opaque-ticket");
    }

    @Test
    void allowsOnlyOneConcurrentExchangeAndRejectsReplay() throws Exception {
        service.create("USER-1", "DEMO_USER", "REACT_NATIVE", "device-1", "https://smart.example");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> exchangeAfterBarrier(ready, start));
            var second = executor.submit(() -> exchangeAfterBarrier(ready, start));
            ready.await();
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(this::exchange).isInstanceOf(SmartMallTicketException.class)
                .extracting("code").isEqualTo("SMART_TICKET_INVALID");
    }

    @Test
    void rejectsExpiredTicket() {
        service.create("USER-1", "DEMO_USER", "REACT_NATIVE", "device-1", "https://smart.example");
        repository.forceExpired = true;

        assertThatThrownBy(this::exchange).isInstanceOf(SmartMallTicketException.class)
                .extracting("code").isEqualTo("SMART_TICKET_EXPIRED");
    }

    @Test
    void wrongOriginOrDeviceDoesNotConsumeTicket() {
        service.create("USER-1", "DEMO_USER", "REACT_NATIVE", "device-1", "https://smart.example");

        assertThatThrownBy(() -> service.exchange("opaque-ticket", "REACT_NATIVE", "device-1",
                "https://evil.example", "1.0")).isInstanceOf(SmartMallTicketException.class)
                .extracting("code").isEqualTo("SMART_TICKET_INVALID");
        assertThatThrownBy(() -> service.exchange("opaque-ticket", "REACT_NATIVE", "device-2",
                "https://smart.example", "1.0")).isInstanceOf(SmartMallTicketException.class)
                .extracting("code").isEqualTo("SMART_TICKET_INVALID");
        assertThat(exchange().userId()).isEqualTo("USER-1");
    }

    private com.hengpick.mall.identity.application.H5Session exchange() {
        return service.exchange("opaque-ticket", "REACT_NATIVE", "device-1", "https://smart.example", "1.0");
    }

    private boolean exchangeAfterBarrier(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            exchange();
            return true;
        } catch (SmartMallTicketException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final class InMemoryTicketRepository implements SmartMallTicketRepository {
        private final Map<String, SmartMallTicket> tickets = new ConcurrentHashMap<>();
        private boolean forceExpired;

        @Override
        public boolean save(SmartMallTicket ticket, Duration ttl) {
            return tickets.putIfAbsent(ticket.ticketHash(), ticket) == null;
        }

        @Override
        public ConsumeResult consume(String ticketHash, String hostType, String deviceSessionId, String h5Origin,
                Instant now) {
            if (forceExpired) {
                tickets.remove(ticketHash);
                return ConsumeResult.expired();
            }
            var consumed = new java.util.concurrent.atomic.AtomicReference<SmartMallTicket>();
            tickets.computeIfPresent(ticketHash, (ignored, ticket) -> {
                if (ticket.hostType().equals(hostType) && ticket.deviceSessionId().equals(deviceSessionId)
                        && ticket.h5Origin().equals(h5Origin)) {
                    consumed.set(ticket);
                    return null;
                }
                return ticket;
            });
            return consumed.get() == null ? ConsumeResult.invalid() : ConsumeResult.consumed(consumed.get());
        }
    }
}
