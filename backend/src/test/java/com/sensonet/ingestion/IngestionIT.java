package com.sensonet.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sensonet.AbstractIT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IngestionIT extends AbstractIT {

    @Autowired IngestionService ingestion;

    @Test
    @DisplayName("Una lectura duplicada (mismo nodo, ts y secuencia) se guarda una sola vez")
    void duplicateStoredOnce() {
        String code = uniqueCode("dup");
        String msg = telemetry(1, Instant.now(), 24.5, 55, 851.0);
        assertThat(ingestion.handleTelemetry(code, msg.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(IngestionService.Outcome.STORED);
        assertThat(ingestion.handleTelemetry(code, msg.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(IngestionService.Outcome.DUPLICATE);
        assertThat(readingCount(code)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una lectura con humedad de 140 % se rechaza y queda registrada como inválida")
    void invalidHumidityIsRecorded() {
        String code = uniqueCode("bad");
        sendTelemetry(code, telemetry(1, Instant.now(), 24.5, 140, null));
        String reason = jdbc.queryForObject(
                "SELECT reason FROM invalid_readings WHERE node_code = ? ORDER BY id DESC LIMIT 1", String.class, code);
        assertThat(reason).contains("Humedad");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nodes WHERE code = ?", Long.class, code)).isZero();
    }

    @Test
    void malformedAndOversizedMessagesAreRejected() {
        String code = uniqueCode("junk");
        sendTelemetry(code, "{no es json");
        sendTelemetry(code, "{\"v\":1,\"pad\":\"" + "x".repeat(5000) + "\"}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invalid_readings WHERE node_code = ?", Long.class, code))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("El estado retenido y el LWT actualizan online/offline, firmware y relé")
    void statusAndLwt() throws Exception {
        String code = uniqueCode("st");
        router.route("sensonet/v1/" + code + "/status",
                "{\"state\":\"online\",\"fw\":\"1.0.0\",\"rssi\":-55,\"buffered\":3,\"dropped\":0,\"relay\":true,\"interval\":10}"
                        .getBytes(StandardCharsets.UTF_8));
        String auth = token(VIEWER);
        JsonNode node = json(mvc.perform(get("/api/nodes/" + nodeId(code)).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(node.get("online").asBoolean()).isTrue();
        assertThat(node.get("firmware").asText()).isEqualTo("1.0.0");
        assertThat(node.get("relayOn").asBoolean()).isTrue();
        assertThat(node.get("buffered").asInt()).isEqualTo(3);

        router.route("sensonet/v1/" + code + "/status", "{\"state\":\"offline\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT online FROM nodes WHERE code = ?", Boolean.class, code)).isFalse();
    }

    @Test
    @DisplayName("El histórico responde en crudo y agregado, y marca las lecturas reenviadas desde el buffer")
    void seriesAndBackfillFlag() throws Exception {
        String code = uniqueCode("hist");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        // 30 lecturas cada 10 s; la más antigua llega 5 minutos tarde (buffer offline)
        for (int i = 0; i < 30; i++) {
            Instant ts = now.minus(Duration.ofSeconds(10L * (30 - i)));
            sendTelemetry(code, telemetry(i, ts, 20 + i * 0.1, 50, 850.0));
        }
        long id = nodeId(code);
        String auth = token(VIEWER);

        JsonNode raw = json(mvc.perform(get("/api/nodes/" + id + "/readings")
                        .param("from", now.minus(Duration.ofHours(1)).toString())
                        .param("to", now.plusSeconds(60).toString())
                        .param("bucket", "raw")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(raw.get("points")).hasSize(30);
        assertThat(raw.get("points").get(0).get("temperature").asDouble()).isEqualTo(20.0);
        // la lectura de hace 300 s llegó "ahora": más de 20 s tarde → rellenada desde el buffer
        assertThat(raw.get("points").get(0).get("backfilled").asBoolean()).isTrue();
        assertThat(raw.get("points").get(29).get("backfilled").asBoolean()).isFalse();

        for (String bucket : new String[] {"1m", "1h", "1d"}) {
            JsonNode agg = json(mvc.perform(get("/api/nodes/" + id + "/readings")
                            .param("from", now.minus(Duration.ofDays(2)).toString())
                            .param("to", now.plusSeconds(60).toString())
                            .param("bucket", bucket)
                            .header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(agg.get("points").size()).as(bucket).isBetween(1, 7);
            assertThat(agg.get("points").get(0).get("humidity").asDouble()).as(bucket).isEqualTo(50.0);
        }

        // Rango demasiado grande para lecturas crudas
        mvc.perform(get("/api/nodes/" + id + "/readings")
                        .param("from", now.minus(Duration.ofDays(10)).toString())
                        .param("bucket", "raw").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lastReadingIsNotOverwrittenByOlderBufferedReading() {
        String code = uniqueCode("last");
        Instant now = Instant.now();
        sendTelemetry(code, telemetry(2, now, 25.0, 50, null));
        sendTelemetry(code, telemetry(1, now.minusSeconds(600), 19.0, 50, null));
        assertThat(jdbc.queryForObject("SELECT last_temperature FROM nodes WHERE code = ?", Double.class, code))
                .isEqualTo(25.0);
        assertThat(readingCount(code)).isEqualTo(2);
    }
}
