package com.sensonet;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensonet.devices.NodeService;
import com.sensonet.ingestion.IngestionService;
import com.sensonet.readings.Bucket;
import com.sensonet.readings.ReadingPoint;
import com.sensonet.readings.ReadingRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Alternativa para proveedores sin TimescaleDB (ADR 0004): PostgreSQL simple con BRIN y date_bin. */
@SpringBootTest
class PostgresFlavorIT {

    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:16-alpine");

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("sensonet.db.flavor", () -> "postgres");
        r.add("sensonet.mqtt.enabled", () -> "false");
        r.add("sensonet.jwt.secret", () -> "secreto-de-pruebas-de-integracion-0123456789");
        r.add("sensonet.seed.demo-rules", () -> "false");
    }

    @Autowired IngestionService ingestion;
    @Autowired ReadingRepository readings;
    @Autowired NodeService nodes;

    @Test
    @DisplayName("Ingesta, deduplicación, agregación y retención funcionan sin TimescaleDB")
    void worksWithPlainPostgres() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 12; i++) {
            String msg = "{\"v\":1,\"seq\":" + i + ",\"ts\":" + now.minusSeconds(600L - 50L * i).toEpochMilli()
                    + ",\"t\":" + (20 + i) + ",\"h\":50}";
            ingestion.handleTelemetry("pg-node", msg.getBytes(StandardCharsets.UTF_8));
            ingestion.handleTelemetry("pg-node", msg.getBytes(StandardCharsets.UTF_8));
        }
        long id = nodes.findByCode("pg-node").orElseThrow().getId();
        assertThat(readings.count(id)).isEqualTo(12);

        List<ReadingPoint> hourly = readings.series(id, now.minus(Duration.ofHours(3)), now, Bucket.HOUR);
        assertThat(hourly).isNotEmpty();
        assertThat(hourly.stream().mapToDouble(ReadingPoint::humidity).max().orElseThrow()).isEqualTo(50.0);
        assertThat(readings.series(id, now.minus(Duration.ofHours(1)), now, Bucket.MINUTE)).hasSizeBetween(10, 13);
        assertThat(readings.series(id, now.minus(Duration.ofDays(3)), now, Bucket.DAY)).hasSizeBetween(1, 2);

        assertThat(readings.deleteOlderThan(now.minusSeconds(300))).isEqualTo(6);
        assertThat(readings.count(id)).isEqualTo(6);
    }
}
