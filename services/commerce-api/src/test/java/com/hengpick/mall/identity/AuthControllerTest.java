package com.hengpick.mall.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.identity.application.AuthService;
import com.hengpick.mall.identity.domain.AuthSession;
import com.hengpick.mall.identity.domain.AuthSessionRepository;
import com.hengpick.mall.identity.domain.RefreshSession;
import com.hengpick.mall.identity.domain.UserAccount;
import com.hengpick.mall.identity.web.AuthController;
import com.hengpick.mall.identity.web.AuthExceptionHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var now = Instant.parse("2026-08-28T08:00:00Z");
        var user = new UserAccount("USER-1", "demo_user", "演示用户", "encoded", "DEMO_USER", "ACTIVE");
        AuthSessionRepository repository = new AuthSessionRepository() {
            @Override
            public Optional<UserAccount> findUserByAccount(String account) {
                return account.equals(user.account()) ? Optional.of(user) : Optional.empty();
            }

            @Override
            public void createSession(AuthSession session) {}

            @Override
            public Optional<RefreshSession> findRefreshSession(String refreshTokenHash, Instant currentTime) {
                return Optional.empty();
            }

            @Override
            public boolean rotateRefreshToken(String sessionId, String expectedHash, String replacementHash,
                    Instant lastUsedAt, Instant expiresAt) {
                return false;
            }
        };
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var service = new AuthService(repository, (plain, hash) -> plain.equals("secret"), () -> "refresh-secret",
                token -> "hash", (account, issuedAt, expiresAt) -> "access-secret", () -> "SESSION-1", clock,
                Duration.ofMinutes(30), Duration.ofDays(7));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(service, clock))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void returnsTokenPairInTheStandardEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"demo_user","password":"secret","deviceSessionId":"device-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-secret"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-secret"))
                .andExpect(jsonPath("$.data.accessTokenExpiresAt").value("2026-08-28T08:30:00Z"))
                .andExpect(jsonPath("$.meta.serverTime").value("2026-08-28T08:00:00Z"));
    }

    @Test
    void rejectsInvalidCredentialsWithoutEchoingThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"demo_user","password":"do-not-echo","deviceSessionId":"device-1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("账号或密码错误"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-echo"))));
    }
}
