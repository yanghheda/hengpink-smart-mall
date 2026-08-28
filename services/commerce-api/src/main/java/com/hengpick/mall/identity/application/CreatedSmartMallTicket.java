package com.hengpick.mall.identity.application;

import java.time.Instant;

public record CreatedSmartMallTicket(String ticket, Instant expiresAt) {}
