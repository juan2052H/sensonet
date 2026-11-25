package com.sensonet;

import com.sensonet.config.SensonetProperties;
import java.time.Duration;
import java.util.List;

/** Propiedades por defecto para pruebas unitarias sin Spring. */
public final class TestProps {

    private TestProps() {}

    public static SensonetProperties defaults() {
        return new SensonetProperties(
                new SensonetProperties.Db("timescale", Duration.ofDays(30)),
                new SensonetProperties.Jwt("test-secret-test-secret-test-secret-1234", Duration.ofHours(8)),
                new SensonetProperties.Cors(List.of("http://localhost:5173")),
                new SensonetProperties.Mqtt(false, "tcp://localhost:1883", "backend", "x", null, "test", 4096),
                new SensonetProperties.Ingestion(true, Duration.ofSeconds(20), Duration.ofDays(2), Duration.ofMinutes(5)),
                new SensonetProperties.Nodes(Duration.ofSeconds(60)),
                new SensonetProperties.Commands(Duration.ofSeconds(10)),
                new SensonetProperties.Telegram(null, null, Duration.ofSeconds(60)),
                new SensonetProperties.RateLimit(10, 600),
                new SensonetProperties.Seed(null, null, null, null, false));
    }
}
