package com.sensonet.rules;

import com.sensonet.alerts.Alert;
import com.sensonet.alerts.AlertService;
import com.sensonet.devices.Node;
import com.sensonet.readings.Reading;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Aplica el motor de reglas a cada lectura nueva y traduce sus decisiones en alertas.
 * El estado (inicio del disparo, alerta abierta) vive en memoria y se reconstruye al arrancar a
 * partir de las alertas abiertas en la base de datos.
 */
@Service
@Order(3)
public class RuleEvaluationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluationService.class);

    private record Key(long ruleId, long nodeId) {}

    private final AlertRuleRepository rules;
    private final AlertService alerts;
    private final Map<Key, RuleState> states = new HashMap<>();
    private final Map<Key, Long> openAlertIds = new HashMap<>();
    private volatile List<AlertRule> enabledRules = List.of();

    public RuleEvaluationService(AlertRuleRepository rules, AlertService alerts) {
        this.rules = rules;
        this.alerts = alerts;
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        reloadRules();
        for (Alert a : alerts.findOpen()) {
            if (a.getRuleId() != null) {
                Key key = new Key(a.getRuleId(), a.getNodeId());
                states.put(key, RuleState.opened());
                openAlertIds.put(key, a.getId());
            }
        }
        log.info("Motor de reglas listo: {} reglas activas, {} alertas abiertas", enabledRules.size(), openAlertIds.size());
    }

    public synchronized void reloadRules() {
        enabledRules = rules.findByEnabledTrue();
    }

    public synchronized void evaluate(Node node, Reading reading) {
        for (AlertRule rule : enabledRules) {
            if (!rule.appliesTo(node.getId())) continue;
            Double value = reading.value(rule.getMetric());
            if (value == null) continue;
            Key key = new Key(rule.getId(), node.getId());
            RuleState state = states.computeIfAbsent(key, k -> new RuleState());
            switch (RuleEngine.evaluate(rule.spec(), state, value, reading.ts())) {
                case OPEN -> {
                    Alert alert = alerts.open(rule, node, value, reading.ts());
                    openAlertIds.put(key, alert.getId());
                    log.info("Alerta abierta: {} en {} (valor {})", rule.getName(), node.getCode(), value);
                }
                case CLOSE -> {
                    Long id = openAlertIds.remove(key);
                    if (id != null) alerts.close(id, reading.ts(), value);
                    log.info("Alerta cerrada: {} en {} (valor {})", rule.getName(), node.getCode(), value);
                }
                case NONE -> {
                    Long id = openAlertIds.get(key);
                    if (state.isOpen() && id != null) alerts.updatePeak(id, value);
                }
            }
        }
    }

    /** Una regla cambió o se eliminó: se olvida su estado y se cierran sus alertas abiertas. */
    public synchronized void onRuleChanged(long ruleId) {
        states.keySet().removeIf(k -> k.ruleId() == ruleId);
        openAlertIds.keySet().removeIf(k -> k.ruleId() == ruleId);
        alerts.closeOpenForRule(ruleId);
        reloadRules();
    }
}
