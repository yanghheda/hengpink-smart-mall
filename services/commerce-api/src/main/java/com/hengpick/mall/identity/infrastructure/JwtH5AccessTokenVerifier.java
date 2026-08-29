package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.RequestSubject;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

/** 校验 H5 短期访问令牌，拒绝把 RN 令牌用于 SSE。 */
public final class JwtH5AccessTokenVerifier {
    private final javax.crypto.SecretKey key;

    public JwtH5AccessTokenVerifier(String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public RequestSubject verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new JwtException("缺少 H5 Access Token");
        }
        var claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(authorization.substring("Bearer ".length())).getPayload();
        if (!"H5_ACCESS".equals(claims.get("tokenType", String.class))) {
            throw new JwtException("凭证类型不正确");
        }
        return new RequestSubject(claims.getSubject(), claims.get("role", String.class));
    }
}
