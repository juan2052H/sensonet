package com.sensonet.alerts;

import com.sensonet.rules.Metric;
import com.sensonet.rules.Operator;
import com.sensonet.rules.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false, length = 80)
    private String ruleName;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Operator operator;

    @Column(nullable = false)
    private double threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "open_value", nullable = false)
    private double openValue;

    @Column(name = "peak_value", nullable = false)
    private double peakValue;

    @Column(name = "acked_by", length = 160)
    private String ackedBy;

    @Column(name = "acked_at")
    private Instant ackedAt;

    protected Alert() {}

    public Alert(Long ruleId, String ruleName, Long nodeId, Metric metric, Operator operator, double threshold,
                 Severity severity, Instant openedAt, double value) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.nodeId = nodeId;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.severity = severity;
        this.openedAt = openedAt;
        this.openValue = value;
        this.peakValue = value;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /** Guarda el valor más extremo alcanzado (máximo para '>', mínimo para '<'). */
    public boolean updatePeak(double value) {
        if (operator.moreExtreme(value, peakValue)) {
            peakValue = value;
            return true;
        }
        return false;
    }

    public void close(Instant at) {
        this.closedAt = at;
    }

    public void ack(String email, Instant at) {
        this.ackedBy = email;
        this.ackedAt = at;
    }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public Long getNodeId() { return nodeId; }
    public Metric getMetric() { return metric; }
    public Operator getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public Severity getSeverity() { return severity; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public double getOpenValue() { return openValue; }
    public double getPeakValue() { return peakValue; }
    public String getAckedBy() { return ackedBy; }
    public Instant getAckedAt() { return ackedAt; }
}
