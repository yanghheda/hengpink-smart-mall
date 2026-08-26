package com.hengpick.mall.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UlidGeneratorTest {

    @Test
    void generatesA26CharacterCrockfordBase32Identifier() {
        var generator = new UlidGenerator(Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));

        var ulid = generator.next();

        assertEquals(26, ulid.length());
        assertTrue(ulid.matches("[0-9A-HJKMNP-TV-Z]{26}"));
    }
}
