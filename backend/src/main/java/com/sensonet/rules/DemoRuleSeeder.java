package com.sensonet.rules;

import com.sensonet.config.SensonetProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Reglas de ejemplo para que la demo genere alertas desde el primer arranque. */
@Component
@Order(2)
public class DemoRuleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRuleSeeder.class);
    private final AlertRuleRepository repo;
    private final SensonetProperties props;

    public DemoRuleSeeder(AlertRuleRepository repo, SensonetProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.seed() == null || !props.seed().demoRules() || repo.count() > 0) {
            return;
        }
        repo.save(new AlertRule(null, "Temperatura alta", Metric.TEMPERATURE, Operator.GT, 30, 28, 300,
                Severity.WARNING, true));
        repo.save(new AlertRule(null, "Temperatura crítica", Metric.TEMPERATURE, Operator.GT, 35, 33, 60,
                Severity.CRITICAL, true));
        repo.save(new AlertRule(null, "Humedad muy baja", Metric.HUMIDITY, Operator.LT, 25, 30, 600,
                Severity.INFO, true));
        log.info("Reglas de demostración creadas");
    }
}
