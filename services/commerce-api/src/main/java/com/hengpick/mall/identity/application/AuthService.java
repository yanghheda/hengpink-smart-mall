package com.hengpick.mall.identity.application;

import com.hengpick.mall.identity.domain.AccessTokenIssuer;
import com.hengpick.mall.identity.domain.AuthSession;
import com.hengpick.mall.identity.domain.AuthSessionRepository;
import com.hengpick.mall.identity.domain.PasswordVerifier;
import com.hengpick.mall.identity.domain.RefreshTokenGenerator;
import com.hengpick.mall.identity.domain.TokenDigester;
import java.time.Clock;
import java.time.Duration;

public class AuthService {
    private final AuthSessionRepository repository;
    private final PasswordVerifier passwordVerifier;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenDigester tokenDigester;
    private final AccessTokenIssuer accessTokenIssuer;
    private final java.util.function.Supplier<String> idGenerator;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public AuthService(
            AuthSessionRepository repository,
            PasswordVerifier passwordVerifier,
            RefreshTokenGenerator refreshTokenGenerator,
            TokenDigester tokenDigester,
            AccessTokenIssuer accessTokenIssuer,
            java.util.function.Supplier<String> idGenerator,
            Clock clock,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {
        this.repository = repository;
        this.passwordVerifier = passwordVerifier;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenDigester = tokenDigester;
        this.accessTokenIssuer = accessTokenIssuer;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public AuthTokens login(String account, String password, String deviceSessionId) {
        var user = repository.findUserByAccount(account).orElse(null);
        if (user == null || !user.isActive() || !passwordVerifier.matches(password, user.passwordHash())) {
            throw new AuthenticationFailedException("账号或密码错误");
        }
        var now = clock.instant();
        var refreshToken = refreshTokenGenerator.generate();
        var refreshExpiresAt = now.plus(refreshTokenTtl);
        repository.createSession(new AuthSession(idGenerator.get(), user.id(), deviceSessionId,
                tokenDigester.digest(refreshToken), "ACTIVE", refreshExpiresAt, null, now));
        return tokensFor(user, refreshToken, now, refreshExpiresAt);
    }

    public AuthTokens refresh(String refreshToken) {
        var now = clock.instant();
        var presentedHash = tokenDigester.digest(refreshToken);
        var session = repository.findRefreshSession(presentedHash, now)
                .orElseThrow(() -> invalidCredential());
        var replacement = refreshTokenGenerator.generate();
        var replacementHash = tokenDigester.digest(replacement);
        var refreshExpiresAt = now.plus(refreshTokenTtl);
        if (!repository.rotateRefreshToken(session.sessionId(), presentedHash, replacementHash, now, refreshExpiresAt)) {
            throw invalidCredential();
        }
        return tokensFor(session.user(), replacement, now, refreshExpiresAt);
    }

    private AuthTokens tokensFor(com.hengpick.mall.identity.domain.UserAccount user, String refreshToken,
            java.time.Instant issuedAt, java.time.Instant refreshExpiresAt) {
        var accessExpiresAt = issuedAt.plus(accessTokenTtl);
        return new AuthTokens("Bearer", accessTokenIssuer.issue(user, issuedAt, accessExpiresAt),
                accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    private AuthenticationFailedException invalidCredential() {
        return new AuthenticationFailedException("登录凭证无效或已过期");
    }
}
