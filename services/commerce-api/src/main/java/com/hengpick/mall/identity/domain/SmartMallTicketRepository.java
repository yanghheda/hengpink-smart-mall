package com.hengpick.mall.identity.domain;

import java.time.Duration;
import java.time.Instant;

public interface SmartMallTicketRepository {
    boolean save(SmartMallTicket ticket, Duration ttl);

    ConsumeResult consume(String ticketHash, String hostType, String deviceSessionId, String h5Origin, Instant now);

    record ConsumeResult(Status status, SmartMallTicket ticket) {
        public static ConsumeResult consumed(SmartMallTicket ticket) {
            return new ConsumeResult(Status.CONSUMED, ticket);
        }

        public static ConsumeResult expired() {
            return new ConsumeResult(Status.EXPIRED, null);
        }

        public static ConsumeResult invalid() {
            return new ConsumeResult(Status.INVALID, null);
        }
    }

    enum Status {
        CONSUMED,
        EXPIRED,
        INVALID
    }
}
