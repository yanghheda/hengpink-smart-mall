package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.H5SessionTokenIssuer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtH5SessionTokenIssuer implements H5SessionTokenIssuer {
    private final SecretKey key;

    public JwtH5SessionTokenIssuer(String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(String userId, String role, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("tokenType", "H5_ACCESS")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
