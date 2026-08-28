package com.hengpick.mall.identity.infrastructure;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

public class JwtRnAccessTokenVerifier {
    private final javax.crypto.SecretKey key;

    public JwtRnAccessTokenVerifier(String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedRnUser verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new JwtException("缺少 RN Access Token");
        }
        var claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(authorization.substring("Bearer ".length())).getPayload();
        if (!"RN_ACCESS".equals(claims.get("tokenType", String.class))) {
            throw new JwtException("凭证类型不正确");
        }
        return new AuthenticatedRnUser(claims.getSubject(), claims.get("role", String.class));
    }

    public record AuthenticatedRnUser(String userId, String role) {}
}
