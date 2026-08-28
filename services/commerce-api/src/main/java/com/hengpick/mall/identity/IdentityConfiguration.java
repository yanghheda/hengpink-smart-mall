package com.hengpick.mall.identity;

import com.hengpick.mall.identity.application.AuthService;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.identity.domain.AuthSessionRepository;
import com.hengpick.mall.identity.domain.DeletionAuditRepository;
import com.hengpick.mall.identity.infrastructure.JwtAccessTokenIssuer;
import com.hengpick.mall.identity.infrastructure.JwtH5SessionTokenIssuer;
import com.hengpick.mall.identity.infrastructure.JwtRnAccessTokenVerifier;
import com.hengpick.mall.identity.infrastructure.RedisSmartMallTicketRepository;
import com.hengpick.mall.identity.infrastructure.SecureTokenGenerator;
import com.hengpick.mall.identity.infrastructure.Sha256TokenDigester;
import com.hengpick.mall.shared.UlidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.identity.application.SmartMallTicketService;

@Configuration
@Profile("database")
public class IdentityConfiguration {
    @Bean
    AuthService authService(AuthSessionRepository repository, IdentityProperties properties,
            UlidGenerator ulidGenerator, Clock clock) {
        var passwordEncoder = new BCryptPasswordEncoder();
        return new AuthService(repository, passwordEncoder::matches, new SecureTokenGenerator(),
                new Sha256TokenDigester(), new JwtAccessTokenIssuer(properties.jwtSecret()), ulidGenerator::next,
                clock, properties.accessTokenTtl(), properties.refreshTokenTtl());
    }

    @Bean
    SmartMallTicketService smartMallTicketService(StringRedisTemplate redis, ObjectMapper objectMapper,
            IdentityProperties properties, Clock clock) {
        return new SmartMallTicketService(new RedisSmartMallTicketRepository(redis, objectMapper),
                new SecureTokenGenerator(), new Sha256TokenDigester(),
                new JwtH5SessionTokenIssuer(properties.jwtSecret()), clock, properties.smartTicketTtl(),
                properties.h5AccessTokenTtl());
    }

    @Bean
    JwtRnAccessTokenVerifier jwtRnAccessTokenVerifier(IdentityProperties properties) {
        return new JwtRnAccessTokenVerifier(properties.jwtSecret());
    }

    @Bean
    ObjectAccessGuard objectAccessGuard(DeletionAuditRepository deletionAuditRepository, Clock clock) {
        return new ObjectAccessGuard(deletionAuditRepository, new Sha256TokenDigester(), clock);
    }
}
