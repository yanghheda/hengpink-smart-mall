package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.AccessTokenIssuer;
import com.hengpick.mall.identity.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private final SecretKey key;

    public JwtAccessTokenIssuer(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(UserAccount user, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(user.id())
                .claim("role", user.role())
                .claim("tokenType", "RN_ACCESS")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
