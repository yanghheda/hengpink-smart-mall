package com.hengpick.mall.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hengpick.identity")
public record IdentityProperties(Duration accessTokenTtl, Duration refreshTokenTtl, Duration smartTicketTtl,
        Duration h5AccessTokenTtl, String jwtSecret) {
    public IdentityProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("Access Token 有效期必须大于零");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("Refresh Token 有效期必须大于零");
        }
        if (!Duration.ofMinutes(5).equals(smartTicketTtl)) {
            throw new IllegalArgumentException("Smart Mall Ticket 有效期必须固定为 5 分钟");
        }
        if (h5AccessTokenTtl == null || h5AccessTokenTtl.isNegative() || h5AccessTokenTtl.isZero()) {
            throw new IllegalArgumentException("H5 Access Token 有效期必须大于零");
        }
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT 签名密钥至少需要 32 字节");
        }
    }
}
