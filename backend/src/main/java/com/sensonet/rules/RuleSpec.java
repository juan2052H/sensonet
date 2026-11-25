package com.sensonet.rules;

import java.time.Duration;

/** Definición mínima de una regla para el motor (sin dependencias de JPA). */
public record RuleSpec(Metric metric, Operator operator, double threshold, double recovery, Duration duration) {

    public RuleSpec {
        if (metric == null || operator == null || duration == null) {
            throw new IllegalArgumentException("metric, operator y duration son obligatorios");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("La duración no puede ser negativa");
        }
        if (!operator.validHysteresis(threshold, recovery)) {
            throw new IllegalArgumentException(operator == Operator.GT
                    ? "Con '>' el umbral de recuperación debe ser menor o igual al de disparo"
                    : "Con '<' el umbral de recuperación debe ser mayor o igual al de disparo");
        }
    }
}
