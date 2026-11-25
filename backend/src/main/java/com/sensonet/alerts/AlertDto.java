package com.sensonet.alerts;

import com.sensonet.rules.Metric;
import com.sensonet.rules.Operator;
import com.sensonet.rules.Severity;
import java.time.Instant;

public record AlertDto(
        Long id,
        Long ruleId,
        String ruleName,
        Long nodeId,
        String nodeName,
        Metric metric,
        String unit,
        Operator operator,
        double threshold,
        Severity severity,
        String status,
        Instant openedAt,
        Instant closedAt,
        double openValue,
        double peakValue,
        String ackedBy,
        Instant ackedAt) {

    public static AlertDto of(Alert a, String nodeName) {
        return new AlertDto(a.getId(), a.getRuleId(), a.getRuleName(), a.getNodeId(), nodeName, a.getMetric(),
                a.getMetric().unit(), a.getOperator(), a.getThreshold(), a.getSeverity(),
                a.isOpen() ? "OPEN" : "CLOSED", a.getOpenedAt(), a.getClosedAt(), a.getOpenValue(),
                a.getPeakValue(), a.getAckedBy(), a.getAckedAt());
    }
}
