package com.hengpick.mall.identity.domain;

import java.time.Instant;

public record SmartMallTicket(
        String ticketHash,
        String userId,
        String role,
        String hostType,
        String deviceSessionId,
        String h5Origin,
        Instant expiresAt) {}
