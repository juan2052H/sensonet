package com.sensonet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FixedWindowLimiterTest {

    @Test
    void limitsPerKeyAndWindow() {
        Clock t0 = Clock.fixed(Instant.parse("2026-11-24T12:00:05Z"), ZoneOffset.UTC);
        FixedWindowLimiter limiter = new FixedWindowLimiter(3, 60_000, t0);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();

        Clock t1 = Clock.fixed(Instant.parse("2026-11-24T12:01:01Z"), ZoneOffset.UTC);
        FixedWindowLimiter next = new FixedWindowLimiter(3, 60_000, t1);
        assertThat(next.tryAcquire("1.1.1.1")).isTrue();
    }
}
