package com.sensonet.readings;

import java.time.Instant;

/**
 * Punto de una serie para el dashboard. {@code backfilled} indica que el dato llegó tarde
 * (reenviado desde el buffer offline del nodo), para dibujarlo distinto en la gráfica.
 */
public record ReadingPoint(Instant ts, Double temperature, Double humidity, Double pressure, boolean backfilled) {}
