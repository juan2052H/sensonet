package com.sensonet.readings;

import com.sensonet.rules.Metric;
import java.time.Instant;

public record Reading(
        long nodeId,
        Instant ts,
        long seq,
        Double temperature,
        Double humidity,
        Double pressure,
        Integer rssi,
        Instant receivedAt) {

    public Double value(Metric metric) {
        return switch (metric) {
            case TEMPERATURE -> temperature;
            case HUMIDITY -> humidity;
            case PRESSURE -> pressure;
            case RSSI -> rssi == null ? null : rssi.doubleValue();
        };
    }
}
