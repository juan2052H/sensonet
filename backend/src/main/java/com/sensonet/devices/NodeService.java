package com.sensonet.devices;

import com.sensonet.common.NotFoundException;
import com.sensonet.config.SensonetProperties;
import com.sensonet.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);
    public static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,39}$");

    private final NodeRepository repo;
    private final RealtimePublisher realtime;
    private final Clock clock;
    private final SensonetProperties props;

    public NodeService(NodeRepository repo, RealtimePublisher realtime, Clock clock, SensonetProperties props) {
        this.repo = repo;
        this.realtime = realtime;
        this.clock = clock;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<Node> list() {
        return repo.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Node get(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Nodo " + id + " no encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<Node> findByCode(String code) {
        return repo.findByCode(code);
    }

    /** Devuelve el nodo; si no existe y el auto-registro está activo, lo crea. */
    @Transactional
    public Optional<Node> findOrRegister(String code) {
        Optional<Node> existing = repo.findByCode(code);
        if (existing.isPresent() || !props.ingestion().autoRegisterNodes() || !CODE.matcher(code).matches()) {
            return existing;
        }
        Node node = repo.save(new Node(code, code));
        log.info("Nodo nuevo registrado automáticamente: {}", code);
        realtime.nodeUpdated(NodeDto.of(node));
        return Optional.of(node);
    }

    /** Registra que llegó una lectura válida del nodo. */
    @Transactional
    public void onReading(long nodeId, Instant ts, Double t, Double h, Double p, Integer rssi) {
        Node node = get(nodeId);
        boolean wasOffline = !node.isOnline();
        node.setOnline(true);
        node.setLastSeen(clock.instant());
        boolean newer = node.applyLatestReading(ts, t, h, p);
        if (newer && rssi != null) {
            node.setRssi(rssi);
        }
        if (wasOffline || newer) {
            realtime.nodeUpdated(NodeDto.of(node));
        }
    }

    /** Aplica el mensaje de estado (incluido el LWT "offline"). */
    @Transactional
    public void applyStatus(String code, StatusPayload status) {
        Optional<Node> maybe = findOrRegister(code);
        if (maybe.isEmpty()) {
            return;
        }
        Node node = repo.findById(maybe.get().getId()).orElseThrow();
        node.setOnline(status.isOnline());
        if (status.isOnline()) {
            node.setLastSeen(clock.instant());
        }
        if (status.fw() != null) node.setFirmware(status.fw());
        if (status.rssi() != null) node.setRssi(status.rssi());
        if (status.buffered() != null) node.setBuffered(status.buffered());
        if (status.dropped() != null) node.setDropped(status.dropped());
        if (status.relay() != null) node.setRelayOn(status.relay());
        if (status.interval() != null && status.interval() > 0) node.setIntervalSeconds(status.interval());
        realtime.nodeUpdated(NodeDto.of(node));
    }

    @Transactional
    public void setRelayState(long nodeId, boolean on) {
        Node node = get(nodeId);
        node.setRelayOn(on);
        realtime.nodeUpdated(NodeDto.of(node));
    }

    @Transactional
    public Node update(long id, String name, String location) {
        Node node = get(id);
        if (name != null && !name.isBlank()) node.setName(name.trim());
        node.setLocation(location == null || location.isBlank() ? null : location.trim());
        realtime.nodeUpdated(NodeDto.of(node));
        return node;
    }

    @Transactional
    public Node setInterval(long id, int seconds) {
        Node node = get(id);
        node.setIntervalSeconds(seconds);
        return node;
    }

    /**
     * Respaldo del LWT: si un nodo deja de enviar datos sin que el broker publique "offline"
     * (p. ej. el broker se reinició), se marca offline tras {@code max(3 intervalos, stale-after)}.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    @Transactional
    public void markStaleNodesOffline() {
        Instant now = clock.instant();
        Duration base = props.nodes().staleAfter();
        for (Node node : repo.findByOnlineTrueAndLastSeenBefore(now.minus(base))) {
            Duration limit = Duration.ofSeconds(Math.max(base.toSeconds(), 3L * node.getIntervalSeconds()));
            if (node.getLastSeen().isBefore(now.minus(limit))) {
                node.setOnline(false);
                log.info("Nodo {} sin datos desde {}: offline", node.getCode(), node.getLastSeen());
                realtime.nodeUpdated(NodeDto.of(node));
            }
        }
    }
}
