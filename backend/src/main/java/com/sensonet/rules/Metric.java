package com.sensonet.rules;

public enum Metric {
    TEMPERATURE("°C"),
    HUMIDITY("%"),
    PRESSURE("hPa"),
    RSSI("dBm");

    private final String unit;

    Metric(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
