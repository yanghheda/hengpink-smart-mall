package com.hengpick.mall.identity.domain;

import java.time.Instant;

@FunctionalInterface
public interface H5SessionTokenIssuer {
    String issue(String userId, String role, Instant issuedAt, Instant expiresAt);
}
