package com.sensonet.devices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mensaje retenido de {@code sensonet/v1/{nodo}/status}. El LWT publica solo {@code {"state":"offline"}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusPayload(
        String state,
        String fw,
        Integer rssi,
        Integer buffered,
        Long dropped,
        Boolean relay,
        Integer interval) {

    public boolean isOnline() {
        return "online".equalsIgnoreCase(state);
    }
}
