package com.sensonet.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensonet.TestProps;
import com.sensonet.alerts.AlertDto;
import com.sensonet.rules.Metric;
import com.sensonet.rules.Operator;
import com.sensonet.rules.Severity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:30:00Z");

    private final List<String> sent = new ArrayList<>();
    private final Notifier fake = new Notifier() {
        @Override public boolean send(String html) { sent.add(html); return true; }
        @Override public boolean enabled() { return true; }
    };

    private AlertDto alert(Instant closedAt) {
        return new AlertDto(1L, 5L, "Temperatura alta", 7L, "Sala <A&B>", Metric.TEMPERATURE, "°C", Operator.GT, 30,
                Severity.WARNING, closedAt == null ? "OPEN" : "CLOSED", T0, closedAt, 31.24, 33.0, null, null);
    }

    @Test
    void openedMessageHasContextAndEscapesHtml() {
        String text = NotificationService.openedText(alert(null), 300);
        assertThat(text)
                .contains("WARNING").contains("Temperatura alta")
                .contains("Sala &lt;A&amp;B&gt;")
                .contains("31.2 °C &gt; 30.0 durante 5 min")
                .contains("2026-11-24 10:30"); // hora de Colombia
    }

    @Test
    void closedMessageShowsPeakAndDuration() {
        String text = NotificationService.closedText(alert(T0.plusSeconds(3900)), 27.5);
        assertThat(text).contains("Recuperada").contains("27.5 °C").contains("33.0 °C").contains("1 h 5 min");
    }

    @Test
    void rateLimitsRepeatedNotificationsForSameRuleAndNode() {
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        NotificationService svc = new NotificationService(fake, clock, TestProps.defaults());
        svc.alertOpened(alert(null), 300);
        svc.alertOpened(alert(null), 300);
        assertThat(sent).hasSize(1);
        assertThat(svc.allow("otra-clave")).isTrue();
    }

    @Test
    void humanDurations() {
        assertThat(NotificationService.humanDuration(45)).isEqualTo("45 s");
        assertThat(NotificationService.humanDuration(600)).isEqualTo("10 min");
        assertThat(NotificationService.humanDuration(7260)).isEqualTo("2 h 1 min");
    }
}
