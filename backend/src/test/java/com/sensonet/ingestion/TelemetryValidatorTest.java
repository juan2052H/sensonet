package com.sensonet.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private final TelemetryValidator v = new TelemetryValidator(Duration.ofDays(2), Duration.ofMinutes(5));

    private TelemetryPayload ok() {
        return new TelemetryPayload(1, 10L, NOW.toEpochMilli(), 24.6, 58.2, 851.3, -61);
    }

    @Test
    void acceptsValidReading() {
        assertThat(v.validate(ok(), NOW).valid()).isTrue();
    }

    @Test
    void pressureIsOptional() {
        var p = new TelemetryPayload(1, 10L, NOW.toEpochMilli(), 24.6, 58.2, null, null);
        assertThat(v.validate(p, NOW).valid()).isTrue();
    }

    @Test
    @DisplayName("Humedad de 140 % se rechaza")
    void rejectsHumidity140() {
        var p = new TelemetryPayload(1, 10L, NOW.toEpochMilli(), 24.6, 140.0, 851.3, -61);
        var r = v.validate(p, NOW);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).contains("Humedad");
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), 90.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), 20.0, 50.0, 200.0, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), 20.0, 50.0, null, 10), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), Double.NaN, 50.0, null, null), NOW).valid()).isFalse();
    }

    @Test
    void rejectsMissingOrWrongSchema() {
        assertThat(v.validate(null, NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(2, 1L, NOW.toEpochMilli(), 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, null, NOW.toEpochMilli(), 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, -1L, NOW.toEpochMilli(), 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, null, 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), null, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, NOW.toEpochMilli(), 20.0, null, null, null), NOW).valid()).isFalse();
    }

    @Test
    void rejectsTimestampsFarInFutureOrPast() {
        long future = NOW.plus(Duration.ofMinutes(6)).toEpochMilli();
        long old = NOW.minus(Duration.ofDays(3)).toEpochMilli();
        long buffered = NOW.minus(Duration.ofHours(5)).toEpochMilli();
        assertThat(v.validate(new TelemetryPayload(1, 1L, future, 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, old, 20.0, 50.0, null, null), NOW).valid()).isFalse();
        assertThat(v.validate(new TelemetryPayload(1, 1L, buffered, 20.0, 50.0, null, null), NOW).valid()).isTrue();
    }
}
