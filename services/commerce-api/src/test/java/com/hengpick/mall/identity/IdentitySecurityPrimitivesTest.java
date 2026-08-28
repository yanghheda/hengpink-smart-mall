package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.identity.domain.UserAccount;
import com.hengpick.mall.identity.infrastructure.JwtAccessTokenIssuer;
import com.hengpick.mall.identity.infrastructure.SecureTokenGenerator;
import com.hengpick.mall.identity.infrastructure.Sha256TokenDigester;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdentitySecurityPrimitivesTest {
    private static final String SECRET = "test-signing-key-with-at-least-thirty-two-bytes";

    @Test
    void issuesVerifiableShortAccessTokenWithOnlyRequiredIdentityClaims() {
        var issuer = new JwtAccessTokenIssuer(SECRET);
        var issuedAt = Instant.parse("2026-08-28T08:00:00Z");
        var expiresAt = issuedAt.plusSeconds(1800);

        var token = issuer.issue(new UserAccount("USER-1", "demo_user", "演示用户", "hash", "DEMO_USER", "ACTIVE"),
                issuedAt, expiresAt);
        var claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> java.util.Date.from(issuedAt))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("USER-1");
        assertThat(claims.get("role")).isEqualTo("DEMO_USER");
        assertThat(claims.get("tokenType")).isEqualTo("RN_ACCESS");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(expiresAt);
        assertThat(claims).doesNotContainKeys("account", "displayName", "refreshToken");
    }

    @Test
    void generatesHighEntropyOpaqueRefreshTokensAndStableHashes() {
        var generator = new SecureTokenGenerator();
        var digester = new Sha256TokenDigester();
        var first = generator.generate();
        var second = generator.generate();

        assertThat(first).hasSizeGreaterThanOrEqualTo(43).doesNotContain("+").doesNotContain("/");
        assertThat(second).isNotEqualTo(first);
        assertThat(digester.digest(first)).hasSize(64).isEqualTo(digester.digest(first));
        assertThat(digester.digest(first)).doesNotContain(first);
    }
}
