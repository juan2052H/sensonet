package com.sensonet.alerts;

import com.sensonet.common.NotFoundException;
import com.sensonet.devices.Node;
import com.sensonet.devices.NodeRepository;
import com.sensonet.notifications.NotificationService;
import com.sensonet.realtime.RealtimePublisher;
import com.sensonet.rules.AlertRule;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida de una alerta: abierta → (reconocida) → cerrada. */
@Service
public class AlertService {

    private final AlertRepository repo;
    private final NodeRepository nodes;
    private final RealtimePublisher realtime;
    private final NotificationService notifications;
    private final Clock clock;

    public AlertService(AlertRepository repo, NodeRepository nodes, RealtimePublisher realtime,
                        NotificationService notifications, Clock clock) {
        this.repo = repo;
        this.nodes = nodes;
        this.realtime = realtime;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Alert open(AlertRule rule, Node node, double value, Instant ts) {
        Alert alert = repo.save(new Alert(rule.getId(), rule.getName(), node.getId(), rule.getMetric(),
                rule.getOperator(), rule.getThreshold(), rule.getSeverity(), ts, value));
        AlertDto dto = AlertDto.of(alert, node.getName());
        realtime.alert(dto);
        notifications.alertOpened(dto, rule.getDurationSeconds());
        return alert;
    }

    @Transactional
    public void updatePeak(long alertId, double value) {
        repo.findById(alertId).ifPresent(a -> {
            if (a.isOpen() && a.updatePeak(value)) {
                realtime.alert(AlertDto.of(a, nodeName(a.getNodeId())));
            }
        });
    }

    @Transactional
    public void close(long alertId, Instant ts, Double lastValue) {
        repo.findById(alertId).filter(Alert::isOpen).ifPresent(a -> {
            a.close(ts);
            AlertDto dto = AlertDto.of(a, nodeName(a.getNodeId()));
            realtime.alert(dto);
            notifications.alertClosed(dto, lastValue);
        });
    }

    /** Cierra las alertas abiertas de una regla modificada o eliminada. */
    @Transactional
    public void closeOpenForRule(long ruleId) {
        Instant now = clock.instant();
        for (Alert a : repo.findByRuleIdAndClosedAtIsNull(ruleId)) {
            a.close(now);
            realtime.alert(AlertDto.of(a, nodeName(a.getNodeId())));
        }
    }

    @Transactional
    public AlertDto ack(long id, String email) {
        Alert a = repo.findById(id).orElseThrow(() -> new NotFoundException("Alerta " + id + " no encontrada"));
        if (a.getAckedAt() == null) {
            a.ack(email, clock.instant());
        }
        AlertDto dto = AlertDto.of(a, nodeName(a.getNodeId()));
        realtime.alert(dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<Alert> findOpen() {
        return repo.findByClosedAtIsNullOrderByOpenedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> list(String status, Long nodeId, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        List<Alert> alerts = switch (status == null ? "ALL" : status.toUpperCase()) {
            case "OPEN" -> repo.findByClosedAtIsNullOrderByOpenedAtDesc();
            case "CLOSED" -> repo.findByClosedAtIsNotNullOrderByOpenedAtDesc(PageRequest.of(0, size));
            case "ALL" -> repo.findAllByOrderByOpenedAtDesc(PageRequest.of(0, size));
            default -> throw new IllegalArgumentException("status debe ser OPEN, CLOSED o ALL");
        };
        Map<Long, String> names = nodes.findAll().stream()
                .collect(Collectors.toMap(Node::getId, Node::getName, (a, b) -> a));
        return alerts.stream()
                .filter(a -> nodeId == null || nodeId.equals(a.getNodeId()))
                .limit(size)
                .map(a -> AlertDto.of(a, names.get(a.getNodeId())))
                .toList();
    }

    private String nodeName(Long nodeId) {
        return nodes.findById(nodeId).map(Node::getName).orElse(null);
    }
}
