package com.sensonet.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Esquema de telemetría v1 (ver docs/ARQUITECTURA.md):
 * <pre>{"v":1,"seq":18452,"ts":1795564800123,"t":24.61,"h":58.2,"p":851.3,"rssi":-61}</pre>
 * {@code p} es opcional (el SHT31 no mide presión).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(
        Integer v,
        Long seq,
        Long ts,
        Double t,
        Double h,
        Double p,
        Integer rssi) {}
