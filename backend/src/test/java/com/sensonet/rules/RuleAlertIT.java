package com.sensonet.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import com.sensonet.AbstractIT;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RuleAlertIT extends AbstractIT {

    private long createRule(String admin, long nodeId) throws Exception {
        String body = """
                {"nodeId":%d,"name":"Calor de prueba","metric":"TEMPERATURE","operator":"GT",
                 "threshold":30,"recovery":28,"durationSeconds":300,"severity":"WARNING"}""".formatted(nodeId);
        return json(mvc.perform(post("/api/rules").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode openAlertsFor(String auth, long nodeId) throws Exception {
        return json(mvc.perform(get("/api/alerts").param("status", "OPEN").param("nodeId", String.valueOf(nodeId))
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Una ola de calor de más de 5 min abre la alerta; al bajar de 28 °C se cierra")
    void heatWaveOpensAndClosesAlert() throws Exception {
        String code = uniqueCode("heat");
        Instant start = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
        sendTelemetry(code, telemetry(0, start, 25, 50, null));
        long nodeId = nodeId(code);
        String admin = token(ADMIN);
        long ruleId = createRule(admin, nodeId);

        // 4 min 30 s por encima de 30 °C: todavía no
        int seq = 1;
        for (int s = 30; s <= 300; s += 30) {
            sendTelemetry(code, telemetry(seq++, start.plusSeconds(s), 31 + (s == 150 ? 2 : 0), 50, null));
        }
        assertThat(openAlertsFor(admin, nodeId)).isEmpty();

        // a los 5 min desde la primera lectura caliente se abre
        sendTelemetry(code, telemetry(seq++, start.plusSeconds(330), 31.5, 50, null));
        JsonNode open = openAlertsFor(admin, nodeId);
        assertThat(open).hasSize(1);
        long alertId = open.get(0).get("id").asLong();
        assertThat(open.get(0).get("peakValue").asDouble()).isEqualTo(31.5);

        // mientras sigue abierta guarda el valor extremo
        sendTelemetry(code, telemetry(seq++, start.plusSeconds(360), 34.2, 50, null));
        // oscila cerca del umbral: no se cierra
        sendTelemetry(code, telemetry(seq++, start.plusSeconds(390), 29.5, 50, null));
        assertThat(openAlertsFor(admin, nodeId)).hasSize(1);
        assertThat(openAlertsFor(admin, nodeId).get(0).get("peakValue").asDouble()).isEqualTo(34.2);

        // reconocer
        mvc.perform(post("/api/alerts/" + alertId + "/ack").header("Authorization", admin))
                .andExpect(status().isOk());
        assertThat(openAlertsFor(admin, nodeId).get(0).get("ackedBy").asString()).isEqualTo(ADMIN);

        // baja de 28 °C → se cierra
        sendTelemetry(code, telemetry(seq++, start.plusSeconds(420), 27.0, 50, null));
        assertThat(openAlertsFor(admin, nodeId)).isEmpty();
        JsonNode all = json(mvc.perform(get("/api/alerts").param("status", "CLOSED").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(all.findValues("id").stream().map(JsonNode::asLong)).contains(alertId);

        // Vista previa sobre el histórico: se habría disparado una vez
        String preview = """
                {"nodeId":%d,"metric":"TEMPERATURE","operator":"GT","threshold":30,"recovery":28,
                 "durationSeconds":300,"from":"%s"}""".formatted(nodeId, start.minusSeconds(60));
        JsonNode result = json(mvc.perform(post("/api/rules/preview").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(preview))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("openings").asInt()).isEqualTo(1);
        assertThat(result.get("intervals").get(0).get("peak").asDouble()).isEqualTo(34.2);

        mvc.perform(delete("/api/rules/" + ruleId).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Modificar una regla cierra sus alertas abiertas")
    void updatingRuleClosesOpenAlerts() throws Exception {
        String code = uniqueCode("upd");
        Instant start = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
        sendTelemetry(code, telemetry(0, start, 25, 50, null));
        long nodeId = nodeId(code);
        String admin = token(ADMIN);
        long ruleId = createRule(admin, nodeId);
        for (int i = 1; i <= 12; i++) {
            sendTelemetry(code, telemetry(i, start.plusSeconds(30L * i), 32, 50, null));
        }
        assertThat(openAlertsFor(admin, nodeId)).hasSize(1);

        String body = """
                {"nodeId":%d,"name":"Calor editada","metric":"TEMPERATURE","operator":"GT",
                 "threshold":40,"recovery":38,"durationSeconds":60,"severity":"CRITICAL","enabled":true}""".formatted(nodeId);
        mvc.perform(put("/api/rules/" + ruleId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(openAlertsFor(admin, nodeId)).isEmpty();
        mvc.perform(get("/api/rules/" + ruleId).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(delete("/api/rules/" + ruleId).header("Authorization", admin)).andExpect(status().isNoContent());
    }
}
