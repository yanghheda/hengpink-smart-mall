package com.hengpick.mall.identity.web;

import com.hengpick.mall.catalog.web.ApiEnvelope;
import com.hengpick.mall.identity.application.AuthService;
import com.hengpick.mall.identity.application.AuthTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("database")
public class AuthController {
    private final AuthService authService;
    private final Clock clock;

    public AuthController(AuthService authService, Clock clock) {
        this.authService = authService;
        this.clock = clock;
    }

    @PostMapping("/login")
    ApiEnvelope<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiEnvelope.success(TokenResponse.from(
                authService.login(request.account(), request.password(), request.deviceSessionId())), clock.instant());
    }

    @PostMapping("/refresh")
    ApiEnvelope<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiEnvelope.success(TokenResponse.from(authService.refresh(request.refreshToken())), clock.instant());
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String account,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 128) String deviceSessionId) {}

    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {}

    public record TokenResponse(
            String tokenType,
            String accessToken,
            String accessTokenExpiresAt,
            String refreshToken,
            String refreshTokenExpiresAt) {
        static TokenResponse from(AuthTokens tokens) {
            return new TokenResponse(tokens.tokenType(), tokens.accessToken(), tokens.accessTokenExpiresAt().toString(),
                    tokens.refreshToken(), tokens.refreshTokenExpiresAt().toString());
        }
    }
}
