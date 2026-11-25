package com.sensonet.devices;

import java.time.Instant;

public record NodeDto(
        Long id,
        String code,
        String name,
        String location,
        String firmware,
        boolean online,
        Instant lastSeen,
        Boolean relayOn,
        Integer rssi,
        Integer buffered,
        Long dropped,
        int intervalSeconds,
        LastReading lastReading) {

    public record LastReading(Instant ts, Double temperature, Double humidity, Double pressure) {}

    public static NodeDto of(Node n) {
        LastReading last = n.getLastReadingAt() == null ? null
                : new LastReading(n.getLastReadingAt(), n.getLastTemperature(), n.getLastHumidity(), n.getLastPressure());
        return new NodeDto(n.getId(), n.getCode(), n.getName(), n.getLocation(), n.getFirmware(), n.isOnline(),
                n.getLastSeen(), n.getRelayOn(), n.getRssi(), n.getBuffered(), n.getDropped(),
                n.getIntervalSeconds(), last);
    }
}
