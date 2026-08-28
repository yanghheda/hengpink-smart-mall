package com.hengpick.mall.identity.domain;

import java.time.Instant;

@FunctionalInterface
public interface AccessTokenIssuer {
    String issue(UserAccount user, Instant issuedAt, Instant expiresAt);
}
