package com.hengpick.mall.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtCallbackTokenCodecTest {
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void tokenOnlyPermitsBoundRunBeforeExpiration() {
        var codec = new JwtCallbackTokenCodec("test-agent-callback-signing-key-at-least-32-bytes");
        var token = codec.issue("RUN-1", NOW, NOW.plusSeconds(120));

        assertThat(codec.permits(token, "RUN-1", NOW.plusSeconds(119))).isTrue();
        assertThat(codec.permits(token, "RUN-2", NOW.plusSeconds(119))).isFalse();
        assertThat(codec.permits(token, "RUN-1", NOW.plusSeconds(120))).isFalse();
        assertThat(codec.permits(token + "tampered", "RUN-1", NOW)).isFalse();
    }
}
