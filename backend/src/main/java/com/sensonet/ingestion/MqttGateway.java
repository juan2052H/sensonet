package com.sensonet.ingestion;

/** Publicación hacia los nodos. Separado de Paho para poder probar los servicios sin broker. */
public interface MqttGateway {

    void publish(String topic, String payload, int qos, boolean retained);

    boolean isConnected();

    class MqttUnavailableException extends RuntimeException {
        public MqttUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
