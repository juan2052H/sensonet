package com.sensonet.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sensonet.common.NotFoundException;
import com.sensonet.config.SensonetProperties;
import com.sensonet.devices.Node;
import com.sensonet.devices.NodeService;
import com.sensonet.ingestion.MqttGateway;
import com.sensonet.ingestion.MqttTopics;
import com.sensonet.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comandos con confirmación: PENDING → (cmd/ack con el mismo id) → CONFIRMED, o FAILED si no
 * llega confirmación a tiempo. El nodo ignora ids repetidos (idempotencia).
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    public static final String RELAY = "RELAY";

    private final CommandRepository repo;
    private final NodeService nodes;
    private final MqttGateway mqtt;
    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SensonetProperties props;

    public CommandService(CommandRepository repo, NodeService nodes, MqttGateway mqtt, RealtimePublisher realtime,
                          ObjectMapper mapper, Clock clock, SensonetProperties props) {
        this.repo = repo;
        this.nodes = nodes;
        this.mqtt = mqtt;
        this.realtime = realtime;
        this.mapper = mapper;
        this.clock = clock;
        this.props = props;
    }

    /** Confirmación publicada por el nodo en {@code cmd/ack}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AckPayload(String id, Boolean ok, Boolean relay, String error) {}

    /**
     * Sin @Transactional a propósito: el comando PENDING debe estar confirmado en la base de datos
     * ANTES de publicarlo; si no, un nodo rápido podría responder el ACK antes del commit y el ACK
     * se descartaría.
     */
    public CommandDto sendRelay(long nodeId, boolean on, String user) {
        Node node = nodes.get(nodeId);
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("on", on);
        String payload = toJson(body);
        Command cmd = repo.save(new Command(id, nodeId, RELAY, payload, user, clock.instant()));
        try {
            mqtt.publish(MqttTopics.relay(node.getCode()), payload, 1, false);
            log.info("Comando {} enviado a {}: relé {}", id, node.getCode(), on ? "ON" : "OFF");
        } catch (MqttGateway.MqttUnavailableException e) {
            cmd.fail("Broker MQTT no disponible", null);
            cmd = repo.save(cmd);
            log.warn("Comando {} fallido: {}", id, e.getMessage());
        }
        CommandDto dto = CommandDto.of(cmd);
        realtime.command(dto);
        return dto;
    }

    @Transactional
    public void handleAck(String nodeCode, AckPayload ack) {
        if (ack == null || ack.id() == null) {
            log.warn("ACK sin id de {}", nodeCode);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(ack.id());
        } catch (IllegalArgumentException e) {
            log.warn("ACK con id inválido de {}: {}", nodeCode, ack.id());
            return;
        }
        Command cmd = repo.findById(id).orElse(null);
        Node node = nodes.findByCode(nodeCode).orElse(null);
        if (cmd == null || node == null || !cmd.getNodeId().equals(node.getId())) {
            log.warn("ACK de {} para comando desconocido o de otro nodo: {}", nodeCode, id);
            return;
        }
        if (ack.relay() != null) {
            nodes.setRelayState(node.getId(), ack.relay());
        }
        if (cmd.getStatus() != CommandStatus.PENDING) {
            log.info("ACK tardío para comando {} (estado {})", id, cmd.getStatus());
            return;
        }
        if (Boolean.FALSE.equals(ack.ok())) {
            cmd.fail(ack.error() == null ? "El nodo reportó un error" : truncate(ack.error()), clock.instant());
        } else {
            cmd.confirm(clock.instant());
        }
        realtime.command(CommandDto.of(cmd));
    }

    /** Marca como FAILED los comandos sin confirmación dentro del tiempo límite (10 s por defecto). */
    @Scheduled(fixedDelay = 1_000)
    @Transactional
    public int expirePending() {
        Instant limit = clock.instant().minus(props.commands().ackTimeout());
        List<Command> expired = repo.findByStatusAndCreatedAtBefore(CommandStatus.PENDING, limit);
        for (Command c : expired) {
            c.fail("Sin confirmación en " + props.commands().ackTimeout().toSeconds() + " s", null);
            realtime.command(CommandDto.of(c));
            log.info("Comando {} marcado FAILED por tiempo de espera", c.getId());
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public CommandDto get(UUID id) {
        return repo.findById(id).map(CommandDto::of)
                .orElseThrow(() -> new NotFoundException("Comando " + id + " no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<CommandDto> recent(long nodeId, int limit) {
        return repo.findByNodeIdOrderByCreatedAtDesc(nodeId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().map(CommandDto::of).toList();
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
