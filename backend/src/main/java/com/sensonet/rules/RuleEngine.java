package com.sensonet.rules;

import java.time.Instant;

/**
 * Motor de reglas con umbral, duración e histéresis. Lógica pura: no toca la base de datos ni
 * MQTT, así que se prueba con pruebas unitarias rápidas.
 *
 * <p>Ejemplo: "si la temperatura es mayor a 30 °C durante 5 minutos, abrir alerta; cerrarla cuando
 * baje de 28 °C". Los dos umbrales evitan que la alerta se abra y cierre muchas veces cuando el
 * valor oscila cerca del límite.
 */
public final class RuleEngine {

    public enum Decision { NONE, OPEN, CLOSE }

    private RuleEngine() {}

    public static Decision evaluate(RuleSpec rule, RuleState state, double value, Instant ts) {
        // Las lecturas reenviadas desde el buffer pueden llegar desordenadas: el motor solo
        // avanza en el tiempo, así una lectura vieja no reabre ni cierra alertas.
        if (state.lastTs() != null && ts.isBefore(state.lastTs())) {
            return Decision.NONE;
        }
        state.lastTs(ts);

        if (state.isOpen()) {
            if (rule.operator().recovers(value, rule.recovery())) {
                state.close();
                return Decision.CLOSE;
            }
            return Decision.NONE;
        }

        if (!rule.operator().triggers(value, rule.threshold())) {
            state.reset();
            return Decision.NONE;
        }

        state.markStartIfMissing(ts);
        if (state.sinceStart(ts).compareTo(rule.duration()) >= 0) {
            state.open();
            return Decision.OPEN;
        }
        return Decision.NONE;
    }
}
