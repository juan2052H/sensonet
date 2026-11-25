package com.sensonet.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sensonet.commands.CommandService;
import com.sensonet.devices.NodeService;
import com.sensonet.devices.StatusPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dirige cada mensaje MQTT entrante al servicio correspondiente según su tópico. */
@Component
public class MqttMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageRouter.class);

    private final IngestionService ingestion;
    private final NodeService nodes;
    private final CommandService commands;
    private final ObjectMapper mapper;

    public MqttMessageRouter(IngestionService ingestion, NodeService nodes, CommandService commands,
                             ObjectMapper mapper) {
        this.ingestion = ingestion;
        this.nodes = nodes;
        this.commands = commands;
        this.mapper = mapper;
    }

    public void route(String topic, byte[] payload) {
        var parsed = MqttTopics.parse(topic);
        if (parsed.isEmpty()) {
            log.debug("Tópico ignorado: {}", topic);
            return;
        }
        String code = parsed.get().nodeCode();
        try {
            switch (parsed.get().kind()) {
                case MqttTopics.TELEMETRY -> ingestion.handleTelemetry(code, payload);
                case MqttTopics.STATUS -> {
                    if (payload.length == 0) return; // borrado del mensaje retenido
                    nodes.applyStatus(code, mapper.readValue(payload, StatusPayload.class));
                }
                case MqttTopics.ACK -> commands.handleAck(code, mapper.readValue(payload, CommandService.AckPayload.class));
                default -> log.debug("Tipo no manejado: {}", topic);
            }
        } catch (Exception e) {
            log.warn("Error procesando {}: {}", topic, e.getMessage());
        }
    }
}
