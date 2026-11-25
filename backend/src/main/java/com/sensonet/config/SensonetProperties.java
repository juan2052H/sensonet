package com.sensonet.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuración propia de SensoNet (prefijo {@code sensonet.} en application.yml). */
@ConfigurationProperties("sensonet")
public record SensonetProperties(
        Db db,
        Jwt jwt,
        Cors cors,
        Mqtt mqtt,
        Ingestion ingestion,
        Nodes nodes,
        Commands commands,
        Telegram telegram,
        RateLimit rateLimit,
        Seed seed) {

    public record Db(@DefaultValue("timescale") String flavor, @DefaultValue("30d") Duration rawRetention) {
        public boolean timescale() {
            return !"postgres".equalsIgnoreCase(flavor);
        }
    }

    public record Jwt(String secret, @DefaultValue("8h") Duration ttl) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Mqtt(
            @DefaultValue("true") boolean enabled,
            String url,
            String username,
            String password,
            String caFile,
            @DefaultValue("sensonet-backend") String clientId,
            @DefaultValue("4096") int maxPayloadBytes) {}

    public record Ingestion(
            @DefaultValue("true") boolean autoRegisterNodes,
            @DefaultValue("20s") Duration backfillThreshold,
            @DefaultValue("2d") Duration maxAge,
            @DefaultValue("5m") Duration maxFuture) {}

    public record Nodes(@DefaultValue("60s") Duration staleAfter) {}

    public record Commands(@DefaultValue("10s") Duration ackTimeout) {}

    public record Telegram(String botToken, String chatId, @DefaultValue("60s") Duration minInterval) {
        public boolean enabled() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }

    public record RateLimit(@DefaultValue("10") int loginPerMinute, @DefaultValue("600") int apiPerMinute) {}

    public record Seed(
            String adminEmail,
            String adminPassword,
            String viewerEmail,
            String viewerPassword,
            @DefaultValue("true") boolean demoRules) {}
}
