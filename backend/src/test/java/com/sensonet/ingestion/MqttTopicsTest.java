package com.sensonet.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqttTopicsTest {

    @Test
    void parsesKnownTopics() {
        assertThat(MqttTopics.parse("sensonet/v1/nodo-sala/telemetry"))
                .contains(new MqttTopics.Parsed("nodo-sala", "telemetry"));
        assertThat(MqttTopics.parse("sensonet/v1/nodo-sala/status"))
                .contains(new MqttTopics.Parsed("nodo-sala", "status"));
        assertThat(MqttTopics.parse("sensonet/v1/nodo-sala/cmd/ack"))
                .contains(new MqttTopics.Parsed("nodo-sala", "cmd/ack"));
    }

    @Test
    void ignoresUnknownTopics() {
        assertThat(MqttTopics.parse("sensonet/v2/nodo/telemetry")).isEmpty();
        assertThat(MqttTopics.parse("sensonet/v1/nodo/cmd/relay")).isEmpty();
        assertThat(MqttTopics.parse("sensonet/v1//telemetry")).isEmpty();
        assertThat(MqttTopics.parse("sensonet/v1/nodo")).isEmpty();
        assertThat(MqttTopics.parse(null)).isEmpty();
    }

    @Test
    void buildsOutgoingTopics() {
        assertThat(MqttTopics.relay("sim-sala")).isEqualTo("sensonet/v1/sim-sala/cmd/relay");
        assertThat(MqttTopics.config("sim-sala")).isEqualTo("sensonet/v1/sim-sala/config");
    }
}
