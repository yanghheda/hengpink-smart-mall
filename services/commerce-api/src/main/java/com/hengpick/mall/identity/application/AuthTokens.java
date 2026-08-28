package com.hengpick.mall.identity.application;

import java.time.Instant;

public record AuthTokens(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {}
