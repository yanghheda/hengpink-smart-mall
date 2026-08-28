package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.AuthSession;
import com.hengpick.mall.identity.domain.AuthSessionRepository;
import com.hengpick.mall.identity.domain.RefreshSession;
import com.hengpick.mall.identity.domain.UserAccount;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class MyBatisAuthSessionRepository implements AuthSessionRepository {
    private final IdentityMapper mapper;

    public MyBatisAuthSessionRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> findUserByAccount(String account) {
        return Optional.ofNullable(mapper.findUserByAccount(account)).map(this::toUser);
    }

    @Override
    public void createSession(AuthSession session) {
        mapper.insertSession(new SessionRow(session.id(), session.userId(), session.deviceSessionId(),
                session.refreshTokenHash(), session.status(), session.expiresAt(), session.lastUsedAt(), session.createdAt()));
    }

    @Override
    public Optional<RefreshSession> findRefreshSession(String refreshTokenHash, Instant now) {
        return Optional.ofNullable(mapper.findRefreshSession(refreshTokenHash, now))
                .map(row -> new RefreshSession(row.sessionId(), new UserAccount(row.userId(), row.account(),
                        row.displayName(), row.passwordHash(), row.role(), row.userStatus()), row.refreshTokenHash()));
    }

    @Override
    public boolean rotateRefreshToken(String sessionId, String expectedHash, String replacementHash,
            Instant lastUsedAt, Instant expiresAt) {
        return mapper.rotateRefreshToken(sessionId, expectedHash, replacementHash, lastUsedAt, expiresAt) == 1;
    }

    private UserAccount toUser(UserRow row) {
        return new UserAccount(row.id(), row.account(), row.displayName(), row.passwordHash(), row.role(), row.status());
    }
}
