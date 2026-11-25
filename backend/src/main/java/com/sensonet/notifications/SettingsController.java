package com.sensonet.notifications;

import com.sensonet.config.SensonetProperties;
import com.sensonet.ingestion.InvalidReadingRepository;
import com.sensonet.ingestion.MqttGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ajustes (ADMIN)")
public class SettingsController {

    private final NotificationService notifications;
    private final InvalidReadingRepository invalid;
    private final MqttGateway mqtt;
    private final SensonetProperties props;

    public SettingsController(NotificationService notifications, InvalidReadingRepository invalid,
                              MqttGateway mqtt, SensonetProperties props) {
        this.notifications = notifications;
        this.invalid = invalid;
        this.mqtt = mqtt;
        this.props = props;
    }

    public record SystemStatus(boolean mqttConnected, String dbFlavor, boolean telegramEnabled,
                               String telegramChatId, long telegramMinIntervalSeconds, long commandTimeoutSeconds) {}

    @Operation(summary = "Estado del sistema: MQTT, base de datos y Telegram")
    @GetMapping("/api/settings/system")
    public SystemStatus system() {
        String chat = props.telegram() == null ? null : props.telegram().chatId();
        String masked = chat == null || chat.isBlank() ? null
                : chat.length() <= 4 ? "****" : "****" + chat.substring(chat.length() - 4);
        return new SystemStatus(mqtt.isConnected(), props.db().flavor(), notifications.enabled(), masked,
                props.telegram() == null ? 60 : props.telegram().minInterval().toSeconds(),
                props.commands().ackTimeout().toSeconds());
    }

    public record TestResult(boolean delivered) {}

    @Operation(summary = "Envía un mensaje de prueba a Telegram")
    @PostMapping("/api/settings/notifications/test")
    public TestResult test() {
        return new TestResult(notifications.sendTest());
    }

    @Operation(summary = "Últimas lecturas rechazadas por validación")
    @GetMapping("/api/settings/invalid-readings")
    public List<InvalidReadingRepository.InvalidReading> invalidReadings(@RequestParam(defaultValue = "50") int limit) {
        return invalid.latest(Math.max(1, Math.min(limit, 500)));
    }
}
