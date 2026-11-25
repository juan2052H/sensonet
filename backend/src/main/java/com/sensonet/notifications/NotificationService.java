package com.sensonet.notifications;

import com.sensonet.alerts.AlertDto;
import com.sensonet.config.SensonetProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Plantillas de mensajes y límite de frecuencia por regla y nodo. */
@Service
public class NotificationService {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final Notifier notifier;
    private final Clock clock;
    private final Duration minInterval;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    public NotificationService(Notifier notifier, Clock clock, SensonetProperties props) {
        this.notifier = notifier;
        this.clock = clock;
        this.minInterval = props.telegram() == null ? Duration.ofMinutes(1) : props.telegram().minInterval();
    }

    @Async
    public void alertOpened(AlertDto a, int durationSeconds) {
        if (!allow("open:" + a.ruleId() + ":" + a.nodeId())) return;
        notifier.send(openedText(a, durationSeconds));
    }

    @Async
    public void alertClosed(AlertDto a, Double lastValue) {
        if (!allow("close:" + a.ruleId() + ":" + a.nodeId())) return;
        notifier.send(closedText(a, lastValue));
    }

    public boolean sendTest() {
        return notifier.send("✅ <b>SensoNet</b>: prueba de notificación (" + TIME.format(clock.instant().atZone(ZONE)) + ")");
    }

    public boolean enabled() {
        return notifier.enabled();
    }

    /** Evita inundar el chat si una misma regla se abre y cierra muchas veces seguidas. */
    boolean allow(String key) {
        Instant now = clock.instant();
        Instant prev = lastSent.get(key);
        if (prev != null && Duration.between(prev, now).compareTo(minInterval) < 0) {
            return false;
        }
        lastSent.put(key, now);
        return true;
    }

    static String openedText(AlertDto a, int durationSeconds) {
        String icon = switch (a.severity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟠";
            case INFO -> "🔵";
        };
        return icon + " <b>" + a.severity() + "</b> · " + esc(a.ruleName()) + "\n"
                + "Nodo: <b>" + esc(a.nodeName()) + "</b>\n"
                + label(a) + " " + fmt(a.openValue()) + " " + a.unit() + " " + esc(a.operator().symbol()) + " "
                + fmt(a.threshold()) + (durationSeconds > 0 ? " durante " + humanDuration(durationSeconds) : "") + "\n"
                + TIME.format(a.openedAt().atZone(ZONE));
    }

    static String closedText(AlertDto a, Double lastValue) {
        return "✅ <b>Recuperada</b> · " + esc(a.ruleName()) + "\n"
                + "Nodo: <b>" + esc(a.nodeName()) + "</b>\n"
                + (lastValue != null ? label(a) + " actual: " + fmt(lastValue) + " " + a.unit() + "\n" : "")
                + "Valor extremo: " + fmt(a.peakValue()) + " " + a.unit() + "\n"
                + "Duración: " + humanDuration(Duration.between(a.openedAt(), a.closedAt()).toSeconds());
    }

    private static String label(AlertDto a) {
        return switch (a.metric()) {
            case TEMPERATURE -> "Temperatura";
            case HUMIDITY -> "Humedad";
            case PRESSURE -> "Presión";
            case RSSI -> "Señal WiFi";
        };
    }

    static String humanDuration(long seconds) {
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return (seconds / 60) + " min";
        return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String esc(String s) {
        if (s == null) return "?";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
