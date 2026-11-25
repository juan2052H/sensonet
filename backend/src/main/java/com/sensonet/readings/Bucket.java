package com.sensonet.readings;

import java.time.Duration;

/** Resolución de una consulta de histórico. */
public enum Bucket {
    RAW("raw", Duration.ZERO, Duration.ofDays(2)),
    MINUTE("1m", Duration.ofMinutes(1), Duration.ofDays(8)),
    HOUR("1h", Duration.ofHours(1), Duration.ofDays(400)),
    DAY("1d", Duration.ofDays(1), Duration.ofDays(3660));

    private final String code;
    private final Duration size;
    private final Duration maxRange;

    Bucket(String code, Duration size, Duration maxRange) {
        this.code = code;
        this.size = size;
        this.maxRange = maxRange;
    }

    public Duration size() {
        return size;
    }

    public Duration maxRange() {
        return maxRange;
    }

    public static Bucket fromCode(String code) {
        for (Bucket b : values()) {
            if (b.code.equalsIgnoreCase(code)) return b;
        }
        throw new IllegalArgumentException("bucket debe ser raw, 1m, 1h o 1d");
    }

    /** Resolución razonable para un rango (≈ 300–1500 puntos). */
    public static Bucket auto(Duration range) {
        if (range.compareTo(Duration.ofHours(3)) <= 0) return RAW;
        if (range.compareTo(Duration.ofDays(2)) <= 0) return MINUTE;
        if (range.compareTo(Duration.ofDays(60)) <= 0) return HOUR;
        return DAY;
    }
}
