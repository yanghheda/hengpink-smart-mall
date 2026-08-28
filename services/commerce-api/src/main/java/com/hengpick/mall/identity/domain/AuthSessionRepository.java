package com.hengpick.mall.identity.domain;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository {
    Optional<UserAccount> findUserByAccount(String account);

    void createSession(AuthSession session);

    Optional<RefreshSession> findRefreshSession(String refreshTokenHash, Instant now);

    boolean rotateRefreshToken(
            String sessionId,
            String expectedHash,
            String replacementHash,
            Instant lastUsedAt,
            Instant expiresAt);
}
