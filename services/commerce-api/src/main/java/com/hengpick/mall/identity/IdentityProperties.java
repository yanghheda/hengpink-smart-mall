package com.hengpick.mall.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hengpick.identity")
public record IdentityProperties(Duration accessTokenTtl, Duration refreshTokenTtl, String jwtSecret) {
    public IdentityProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("Access Token 有效期必须大于零");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("Refresh Token 有效期必须大于零");
        }
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT 签名密钥至少需要 32 字节");
        }
    }
}
