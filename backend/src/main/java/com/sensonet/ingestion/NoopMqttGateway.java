package com.sensonet.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Se usa cuando {@code sensonet.mqtt.enabled=false}: cualquier intento de publicar falla de forma explícita. */
@Component
@ConditionalOnProperty(prefix = "sensonet.mqtt", name = "enabled", havingValue = "false")
public class NoopMqttGateway implements MqttGateway {

    @Override
    public void publish(String topic, String payload, int qos, boolean retained) {
        throw new MqttUnavailableException("MQTT deshabilitado (sensonet.mqtt.enabled=false)", null);
    }

    @Override
    public boolean isConnected() {
        return false;
    }
}
