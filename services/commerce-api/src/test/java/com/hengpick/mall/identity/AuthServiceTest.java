package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.identity.application.AuthService;
import com.hengpick.mall.identity.application.AuthenticationFailedException;
import com.hengpick.mall.identity.domain.AccessTokenIssuer;
import com.hengpick.mall.identity.domain.AuthSession;
import com.hengpick.mall.identity.domain.AuthSessionRepository;
import com.hengpick.mall.identity.domain.PasswordVerifier;
import com.hengpick.mall.identity.domain.RefreshSession;
import com.hengpick.mall.identity.domain.RefreshTokenGenerator;
import com.hengpick.mall.identity.domain.TokenDigester;
import com.hengpick.mall.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");
    private InMemoryAuthSessionRepository repository;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuthSessionRepository();
        repository.users.put("demo_user", new UserAccount("USER-1", "demo_user", "演示用户", "encoded-secret", "DEMO_USER", "ACTIVE"));
        repository.users.put("disabled_user", new UserAccount("USER-2", "disabled_user", "停用用户", "encoded-secret", "DEMO_USER", "DISABLED"));
        var refreshTokens = new java.util.concurrent.ConcurrentLinkedQueue<>(java.util.List.of("refresh-1", "refresh-2", "refresh-3"));
        RefreshTokenGenerator refreshTokenGenerator = refreshTokens::remove;
        TokenDigester tokenDigester = token -> "sha256:" + token;
        AccessTokenIssuer accessTokenIssuer = (user, issuedAt, expiresAt) -> "access:" + user.id() + ":" + expiresAt;
        PasswordVerifier passwordVerifier = (plain, encoded) -> plain.equals("secret") && encoded.equals("encoded-secret");
        service = new AuthService(repository, passwordVerifier, refreshTokenGenerator, tokenDigester,
                accessTokenIssuer, () -> "SESSION-1", Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30), Duration.ofDays(7));
    }

    @Test
    void rotatesRefreshTokenAndRejectsTheOldToken() {
        var login = service.login("demo_user", "secret", "device-1");

        var refreshed = service.refresh(login.refreshToken());

        assertThat(refreshed.refreshToken()).isEqualTo("refresh-2");
        assertThat(repository.sessions.get("SESSION-1").refreshTokenHash()).isEqualTo("sha256:refresh-2");
        assertThat(repository.persistedValues()).doesNotContain("refresh-1", "refresh-2");
        assertThatThrownBy(() -> service.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("登录凭证无效或已过期");
    }

    @Test
    void allowsOnlyOneWinnerWhenTheSameRefreshTokenIsRotatedConcurrently() throws Exception {
        var login = service.login("demo_user", "secret", "device-1");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> refreshAfterBarrier(login.refreshToken(), ready, start));
            var second = executor.submit(() -> refreshAfterBarrier(login.refreshToken(), ready, start));
            ready.await();
            start.countDown();

            var outcomes = java.util.List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn("success", true).hasSize(1);
            assertThat(outcomes).filteredOn("success", false).hasSize(1);
        }
    }

    @Test
    void rejectsDisabledUsersWithoutCreatingASession() {
        assertThatThrownBy(() -> service.login("disabled_user", "secret", "device-1"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("账号或密码错误");
        assertThat(repository.sessions).isEmpty();
    }

    @Test
    void rejectsWrongPasswordWithTheSamePublicError() {
        assertThatThrownBy(() -> service.login("demo_user", "wrong", "device-1"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("账号或密码错误");
        assertThat(repository.sessions).isEmpty();
    }

    private RefreshOutcome refreshAfterBarrier(String token, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            service.refresh(token);
            return new RefreshOutcome(true);
        } catch (AuthenticationFailedException exception) {
            return new RefreshOutcome(false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new RefreshOutcome(false);
        }
    }

    private record RefreshOutcome(boolean success) {}

    private static final class InMemoryAuthSessionRepository implements AuthSessionRepository {
        private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
        private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

        @Override
        public Optional<UserAccount> findUserByAccount(String account) {
            return Optional.ofNullable(users.get(account));
        }

        @Override
        public void createSession(AuthSession session) {
            sessions.put(session.id(), session);
        }

        @Override
        public Optional<RefreshSession> findRefreshSession(String refreshTokenHash, Instant now) {
            return sessions.values().stream()
                    .filter(session -> session.refreshTokenHash().equals(refreshTokenHash))
                    .filter(session -> session.status().equals("ACTIVE") && session.expiresAt().isAfter(now))
                    .findFirst()
                    .map(session -> new RefreshSession(session.id(), users.values().stream()
                            .filter(user -> user.id().equals(session.userId())).findFirst().orElseThrow(),
                            session.refreshTokenHash()));
        }

        @Override
        public boolean rotateRefreshToken(String sessionId, String expectedHash, String replacementHash,
                Instant lastUsedAt, Instant expiresAt) {
            return sessions.computeIfPresent(sessionId, (ignored, current) -> {
                if (!current.refreshTokenHash().equals(expectedHash)) {
                    return current;
                }
                return new AuthSession(current.id(), current.userId(), current.deviceSessionId(), replacementHash,
                        current.status(), expiresAt, lastUsedAt, current.createdAt());
            }).refreshTokenHash().equals(replacementHash);
        }

        private java.util.List<String> persistedValues() {
            return sessions.values().stream()
                    .flatMap(session -> java.util.stream.Stream.of(session.refreshTokenHash(), session.deviceSessionId()))
                    .toList();
        }
    }
}
