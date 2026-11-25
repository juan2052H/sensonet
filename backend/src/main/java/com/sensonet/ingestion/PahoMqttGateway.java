package com.sensonet.ingestion;

import com.sensonet.config.SensonetProperties;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Cliente MQTT del backend (Eclipse Paho). Se conecta con TLS y usuario propio, se suscribe a
 * telemetría, estado y confirmaciones, y entrega cada mensaje a un hilo dedicado para no bloquear
 * el hilo de red de Paho.
 */
@Component
@ConditionalOnProperty(prefix = "sensonet.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PahoMqttGateway implements MqttGateway, MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(PahoMqttGateway.class);

    private final SensonetProperties.Mqtt props;
    private final ObjectProvider<MqttMessageRouter> router;
    private final ThreadPoolTaskExecutor inbound;
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mqtt-connector");
        t.setDaemon(true);
        return t;
    });
    private volatile MqttClient client;

    public PahoMqttGateway(SensonetProperties properties, ObjectProvider<MqttMessageRouter> router,
                           @Qualifier("mqttInboundExecutor") ThreadPoolTaskExecutor inbound) {
        this.props = properties.mqtt();
        this.router = router;
        this.inbound = inbound;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connector.execute(this::connectLoop);
    }

    /** La reconexión automática de Paho solo actúa tras la primera conexión; hasta entonces reintentamos aquí. */
    private void connectLoop() {
        try {
            if (client == null) {
                client = new MqttClient(props.url(), props.clientId(), new MemoryPersistence());
                client.setCallback(this);
                client.setTimeToWait(5_000);
            }
            client.connect(options());
            log.info("Conectado al broker MQTT {}", props.url());
        } catch (Exception e) {
            log.warn("No se pudo conectar a {} ({}); reintento en 5 s", props.url(), e.getMessage());
            if (!connector.isShutdown()) {
                connector.schedule(this::connectLoop, 5, TimeUnit.SECONDS);
            }
        }
    }

    private MqttConnectOptions options() throws Exception {
        MqttConnectOptions o = new MqttConnectOptions();
        o.setAutomaticReconnect(true);
        // Sesión persistente: si el backend se reinicia, el broker guarda los mensajes QoS 1 pendientes.
        o.setCleanSession(false);
        o.setKeepAliveInterval(30);
        o.setConnectionTimeout(10);
        o.setMaxInflight(100);
        if (props.username() != null && !props.username().isBlank()) {
            o.setUserName(props.username());
        }
        if (props.password() != null && !props.password().isBlank()) {
            o.setPassword(props.password().toCharArray());
        }
        if (props.url().startsWith("ssl://")) {
            o.setHttpsHostnameVerificationEnabled(true);
            if (props.caFile() != null && !props.caFile().isBlank()) {
                o.setSocketFactory(SslSocketFactories.trustingCa(props.caFile()));
            }
        }
        return o;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(new String[] {MqttTopics.SUB_TELEMETRY, MqttTopics.SUB_STATUS, MqttTopics.SUB_ACK},
                    new int[] {1, 1, 1});
            log.info("{} a {}; suscrito a telemetría, estado y confirmaciones",
                    reconnect ? "Reconectado" : "Conectado", serverURI);
        } catch (MqttException e) {
            log.error("No se pudo suscribir: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida: {}", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        byte[] payload = message.getPayload().clone();
        inbound.execute(() -> router.getObject().route(topic, payload));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no se usa
    }

    @Override
    public void publish(String topic, String payload, int qos, boolean retained) {
        MqttClient c = client;
        if (c == null || !c.isConnected()) {
            throw new MqttUnavailableException("El broker MQTT no está disponible", null);
        }
        try {
            c.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
        } catch (MqttException e) {
            throw new MqttUnavailableException("No se pudo publicar en " + topic, e);
        }
    }

    @Override
    public boolean isConnected() {
        MqttClient c = client;
        return c != null && c.isConnected();
    }

    @PreDestroy
    public void stop() {
        connector.shutdownNow();
        MqttClient c = client;
        if (c != null) {
            try {
                if (c.isConnected()) c.disconnect(2_000);
                c.close();
            } catch (MqttException e) {
                log.debug("Error cerrando MQTT: {}", e.getMessage());
            }
        }
    }
}
