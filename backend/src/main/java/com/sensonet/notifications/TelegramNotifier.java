package com.sensonet.notifications;

import com.sensonet.config.SensonetProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Envía mensajes con la Bot API de Telegram (sendMessage). */
@Component
public class TelegramNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private final SensonetProperties.Telegram props;
    private final RestClient http;

    public TelegramNotifier(SensonetProperties properties, RestClient.Builder builder) {
        this.props = properties.telegram();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.baseUrl("https://api.telegram.org").requestFactory(rf).build();
    }

    @Override
    public boolean enabled() {
        return props != null && props.enabled();
    }

    @Override
    public boolean send(String html) {
        if (!enabled()) {
            log.info("[Telegram deshabilitado] {}", html.replaceAll("<[^>]+>", ""));
            return false;
        }
        try {
            http.post()
                    .uri("/bot{token}/sendMessage", props.botToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", props.chatId(), "text", html, "parse_mode", "HTML",
                            "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // No se registra la URL: contiene el token del bot.
            log.warn("No se pudo enviar el mensaje a Telegram: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
