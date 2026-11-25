package com.sensonet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sensonet.ingestion.MqttGateway;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Un "nodo" Paho real publica en el broker real y el backend procesa todo el flujo. */
class MqttEndToEndIT extends AbstractIT {

    @Autowired MqttGateway gateway;
    private MqttClient node;
    private String code;

    @BeforeEach
    void connectFakeNode() throws Exception {
        await().atMost(Duration.ofSeconds(30)).until(gateway::isConnected);
        code = uniqueCode("e2e");
        node = new MqttClient(mqttUrl(), code, new MemoryPersistence());
        MqttConnectOptions o = new MqttConnectOptions();
        o.setCleanSession(true);
        o.setWill("sensonet/v1/" + code + "/status", "{\"state\":\"offline\"}".getBytes(StandardCharsets.UTF_8), 1, true);
        node.connect(o);
    }

    @AfterEach
    void disconnect() throws Exception {
        if (node.isConnected()) node.disconnect();
        node.close();
    }

    @Test
    @DisplayName("Telemetría publicada por MQTT llega a la base de datos (y los duplicados QoS 1 no se repiten)")
    void telemetryOverMqtt() throws Exception {
        String msg = telemetry(1, Instant.now(), 23.4, 51, 849.5);
        node.publish("sensonet/v1/" + code + "/telemetry", msg.getBytes(StandardCharsets.UTF_8), 1, false);
        node.publish("sensonet/v1/" + code + "/telemetry", msg.getBytes(StandardCharsets.UTF_8), 1, false);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM nodes WHERE code = ?", Long.class, code) == 1
                        && readingCount(code) == 1);
        Thread.sleep(300);
        assertThat(readingCount(code)).isEqualTo(1);
    }

    @Test
    @DisplayName("Comando al relé: el nodo confirma con el mismo id y el comando queda CONFIRMED")
    void relayCommandIsConfirmed() throws Exception {
        node.publish("sensonet/v1/" + code + "/status",
                "{\"state\":\"online\",\"relay\":false}".getBytes(StandardCharsets.UTF_8), 1, true);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM nodes WHERE code = ?", Long.class, code) == 1);

        node.subscribe("sensonet/v1/" + code + "/cmd/relay", 1, (topic, m) -> {
            var cmd = mapper.readTree(m.getPayload());
            String ack = "{\"id\":\"" + cmd.get("id").asText() + "\",\"ok\":true,\"relay\":" + cmd.get("on").asBoolean() + "}";
            // publicar desde otro hilo: Paho no permite publicar QoS 1 dentro del callback
            new Thread(() -> {
                try {
                    node.publish("sensonet/v1/" + code + "/cmd/ack", new MqttMessage(ack.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });

        String admin = token(ADMIN);
        long id = nodeId(code);
        String body = mvc.perform(post("/api/nodes/" + id + "/commands/relay").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String cmdId = json(body).get("id").asText();
        assertThat(json(body).get("status").asText()).isIn("PENDING", "CONFIRMED");

        await().atMost(Duration.ofSeconds(8)).until(() -> json(mvc.perform(get("/api/commands/" + cmdId)
                        .header("Authorization", admin)).andReturn().getResponse().getContentAsString())
                .get("status").asText().equals("CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT relay_on FROM nodes WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("Si el nodo se desconecta sin avisar, el LWT lo marca offline")
    void lastWillMarksNodeOffline() throws Exception {
        node.publish("sensonet/v1/" + code + "/status",
                "{\"state\":\"online\"}".getBytes(StandardCharsets.UTF_8), 1, true);
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(
                jdbc.query("SELECT online FROM nodes WHERE code = ?", rs -> rs.next() ? rs.getBoolean(1) : null, code)));
        node.disconnectForcibly(0, 0, false); // corte abrupto: el broker publica el LWT
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(
                jdbc.queryForObject("SELECT online FROM nodes WHERE code = ?", Boolean.class, code)));
    }
}
