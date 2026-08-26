package com.hengpick.mall.shared;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;

/** Generates sortable, 26-character ULIDs for identifiers shared across Java and Python. */
public final class UlidGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final Clock clock;
    private final SecureRandom random;

    public UlidGenerator(Clock clock) {
        this(clock, new SecureRandom());
    }

    UlidGenerator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    public String next() {
        var timestamp = clock.millis();
        if (timestamp < 0 || timestamp >= (1L << 48)) {
            throw new IllegalStateException("ULID timestamp must fit in 48 bits");
        }

        var value = new StringBuilder(26);
        for (var shift = 45; shift >= 0; shift -= 5) {
            value.append(CROCKFORD_BASE32[(int) ((timestamp >>> shift) & 0x1F)]);
        }

        var randomness = new byte[10];
        random.nextBytes(randomness);
        var randomValue = new BigInteger(1, randomness);
        for (var index = 15; index >= 0; index--) {
            value.append(CROCKFORD_BASE32[randomValue.shiftRight(index * 5).intValue() & 0x1F]);
        }
        return value.toString();
    }
}
