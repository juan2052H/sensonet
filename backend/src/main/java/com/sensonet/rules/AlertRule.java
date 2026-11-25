package com.sensonet.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = aplica a todos los nodos. */
    @Column(name = "node_id")
    private Long nodeId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Operator operator;

    @Column(nullable = false)
    private double threshold;

    @Column(nullable = false)
    private double recovery;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AlertRule() {}

    public AlertRule(Long nodeId, String name, Metric metric, Operator operator, double threshold,
                     double recovery, int durationSeconds, Severity severity, boolean enabled) {
        apply(nodeId, name, metric, operator, threshold, recovery, durationSeconds, severity, enabled);
    }

    public final void apply(Long nodeId, String name, Metric metric, Operator operator, double threshold,
                            double recovery, int durationSeconds, Severity severity, boolean enabled) {
        // Valida la combinación (histéresis coherente) antes de aceptar los cambios.
        new RuleSpec(metric, operator, threshold, recovery, Duration.ofSeconds(durationSeconds));
        this.nodeId = nodeId;
        this.name = name;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.recovery = recovery;
        this.durationSeconds = durationSeconds;
        this.severity = severity;
        this.enabled = enabled;
    }

    public RuleSpec spec() {
        return new RuleSpec(metric, operator, threshold, recovery, Duration.ofSeconds(durationSeconds));
    }

    public boolean appliesTo(long nodeId) {
        return this.nodeId == null || this.nodeId == nodeId;
    }

    public Long getId() { return id; }
    public Long getNodeId() { return nodeId; }
    public String getName() { return name; }
    public Metric getMetric() { return metric; }
    public Operator getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public double getRecovery() { return recovery; }
    public int getDurationSeconds() { return durationSeconds; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
