package com.hengpick.mall.integration.agent;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** 使用 HS256 生成短期回调 Token，声明中只包含 Run 标识和用途。 */
public final class JwtCallbackTokenCodec implements CallbackTokenCodec {
    private final SecretKey key;

    public JwtCallbackTokenCodec(String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(String runId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(runId)
                .claim("tokenType", "AGENT_CALLBACK")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public boolean permits(String token, String runId, Instant now) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(now))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return runId.equals(claims.getSubject())
                    && "AGENT_CALLBACK".equals(claims.get("tokenType", String.class))
                    && claims.getExpiration().toInstant().isAfter(now);
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}
