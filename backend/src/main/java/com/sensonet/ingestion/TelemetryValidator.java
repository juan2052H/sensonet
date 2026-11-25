package com.sensonet.ingestion;

import java.time.Duration;
import java.time.Instant;

/** Validación de esquema y de rango físico. Lógica pura: no depende de Spring, base de datos ni MQTT. */
public final class TelemetryValidator {

    public static final int SCHEMA_VERSION = 1;
    public static final double T_MIN = -40, T_MAX = 85;     // BME280 / SHT31
    public static final double H_MIN = 0, H_MAX = 100;
    public static final double P_MIN = 300, P_MAX = 1100;   // Medellín ≈ 850 hPa
    public static final int RSSI_MIN = -120, RSSI_MAX = 0;

    private final Duration maxAge;
    private final Duration maxFuture;

    public TelemetryValidator(Duration maxAge, Duration maxFuture) {
        this.maxAge = maxAge;
        this.maxFuture = maxFuture;
    }

    public Result validate(TelemetryPayload p, Instant now) {
        if (p == null) return Result.invalid("Mensaje vacío");
        if (p.v() == null || p.v() != SCHEMA_VERSION) return Result.invalid("Versión de esquema no soportada: " + p.v());
        if (p.seq() == null || p.seq() < 0) return Result.invalid("seq ausente o negativo");
        if (p.ts() == null) return Result.invalid("ts ausente");
        Instant ts = Instant.ofEpochMilli(p.ts());
        if (ts.isAfter(now.plus(maxFuture))) return Result.invalid("ts en el futuro: " + ts);
        if (ts.isBefore(now.minus(maxAge))) return Result.invalid("ts demasiado antiguo: " + ts);
        if (p.t() == null) return Result.invalid("Temperatura ausente");
        if (!inRange(p.t(), T_MIN, T_MAX)) return Result.invalid("Temperatura fuera de rango físico: " + p.t());
        if (p.h() == null) return Result.invalid("Humedad ausente");
        if (!inRange(p.h(), H_MIN, H_MAX)) return Result.invalid("Humedad fuera de rango físico: " + p.h());
        if (p.p() != null && !inRange(p.p(), P_MIN, P_MAX)) return Result.invalid("Presión fuera de rango físico: " + p.p());
        if (p.rssi() != null && (p.rssi() < RSSI_MIN || p.rssi() > RSSI_MAX)) return Result.invalid("RSSI fuera de rango: " + p.rssi());
        return Result.ok();
    }

    private static boolean inRange(double v, double min, double max) {
        return !Double.isNaN(v) && v >= min && v <= max;
    }

    public record Result(boolean valid, String reason) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result invalid(String reason) {
            return new Result(false, reason);
        }
    }
}
