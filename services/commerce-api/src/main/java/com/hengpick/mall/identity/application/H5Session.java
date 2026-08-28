package com.hengpick.mall.identity.application;

import java.time.Instant;

public record H5Session(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String userId,
        String role,
        String hostType,
        String deviceSessionId) {}
