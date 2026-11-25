package com.sensonet.ingestion;

import java.util.Optional;

/** Diseño de tópicos v1: {@code sensonet/v1/{nodo}/{tipo}}. */
public final class MqttTopics {

    public static final String PREFIX = "sensonet/v1/";
    public static final String SUB_TELEMETRY = PREFIX + "+/telemetry";
    public static final String SUB_STATUS = PREFIX + "+/status";
    public static final String SUB_ACK = PREFIX + "+/cmd/ack";

    public static final String TELEMETRY = "telemetry";
    public static final String STATUS = "status";
    public static final String ACK = "cmd/ack";

    private MqttTopics() {}

    public static String relay(String nodeCode) {
        return PREFIX + nodeCode + "/cmd/relay";
    }

    public static String config(String nodeCode) {
        return PREFIX + nodeCode + "/config";
    }

    public record Parsed(String nodeCode, String kind) {}

    /** Devuelve el código del nodo y el tipo de mensaje, o vacío si el tópico no pertenece a v1. */
    public static Optional<Parsed> parse(String topic) {
        if (topic == null || !topic.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = topic.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return Optional.empty();
        }
        String code = rest.substring(0, slash);
        String kind = rest.substring(slash + 1);
        if (!kind.equals(TELEMETRY) && !kind.equals(STATUS) && !kind.equals(ACK)) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(code, kind));
    }
}
