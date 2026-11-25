package com.sensonet.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sensonet.config.SensonetProperties;
import com.sensonet.devices.Node;
import com.sensonet.devices.NodeService;
import com.sensonet.readings.Reading;
import com.sensonet.readings.ReadingPoint;
import com.sensonet.readings.ReadingRepository;
import com.sensonet.realtime.RealtimePublisher;
import com.sensonet.rules.RuleEvaluationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Procesa la telemetría: tamaño → JSON → esquema y rango físico → nodo → inserción idempotente
 * → WebSocket → motor de reglas.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public enum Outcome { STORED, DUPLICATE, INVALID }

    private final ObjectMapper mapper;
    private final NodeService nodes;
    private final ReadingRepository readings;
    private final InvalidReadingRepository invalid;
    private final RealtimePublisher realtime;
    private final RuleEvaluationService rules;
    private final Clock clock;
    private final TelemetryValidator validator;
    private final SensonetProperties props;
    private final Counter stored;
    private final Counter duplicates;
    private final Counter rejected;
    private final Timer lag;

    public IngestionService(ObjectMapper mapper, NodeService nodes, ReadingRepository readings,
                            InvalidReadingRepository invalid, RealtimePublisher realtime,
                            RuleEvaluationService rules, Clock clock, SensonetProperties props,
                            MeterRegistry metrics) {
        this.mapper = mapper;
        this.nodes = nodes;
        this.readings = readings;
        this.invalid = invalid;
        this.realtime = realtime;
        this.rules = rules;
        this.clock = clock;
        this.props = props;
        this.validator = new TelemetryValidator(props.ingestion().maxAge(), props.ingestion().maxFuture());
        this.stored = metrics.counter("sensonet.readings", "result", "stored");
        this.duplicates = metrics.counter("sensonet.readings", "result", "duplicate");
        this.rejected = metrics.counter("sensonet.readings", "result", "invalid");
        this.lag = Timer.builder("sensonet.ingest.lag")
                .description("Retraso entre la medición en el nodo y su recepción en el backend")
                .publishPercentiles(0.5, 0.95)
                .register(metrics);
    }

    public Outcome handleTelemetry(String nodeCode, byte[] payload) {
        String raw = new String(payload, StandardCharsets.UTF_8);
        if (payload.length > props.mqtt().maxPayloadBytes()) {
            return reject(nodeCode, "Mensaje demasiado grande (" + payload.length + " bytes)", raw.substring(0, 200));
        }
        TelemetryPayload p;
        try {
            p = mapper.readValue(payload, TelemetryPayload.class);
        } catch (JacksonException e) {
            return reject(nodeCode, "JSON inválido", raw);
        } catch (Exception e) {
            return reject(nodeCode, "JSON ilegible", raw);
        }
        Instant now = clock.instant();
        TelemetryValidator.Result result = validator.validate(p, now);
        if (!result.valid()) {
            return reject(nodeCode, result.reason(), raw);
        }
        Optional<Node> node = nodes.findOrRegister(nodeCode);
        if (node.isEmpty()) {
            return reject(nodeCode, "Nodo desconocido", raw);
        }
        long nodeId = node.get().getId();
        Instant ts = Instant.ofEpochMilli(p.ts());
        Reading reading = new Reading(nodeId, ts, p.seq(), p.t(), p.h(), p.p(), p.rssi(), now);

        if (!readings.insert(reading)) {
            duplicates.increment();
            log.debug("Lectura duplicada descartada: nodo={} seq={} ts={}", nodeCode, p.seq(), ts);
            return Outcome.DUPLICATE;
        }
        stored.increment();
        Duration delay = Duration.between(ts, now);
        if (!delay.isNegative()) {
            lag.record(delay);
        }
        nodes.onReading(nodeId, ts, p.t(), p.h(), p.p(), p.rssi());
        boolean backfilled = delay.compareTo(props.ingestion().backfillThreshold()) > 0;
        realtime.reading(nodeId, new ReadingPoint(ts, p.t(), p.h(), p.p(), backfilled));
        rules.evaluate(node.get(), reading);
        return Outcome.STORED;
    }

    private Outcome reject(String nodeCode, String reason, String raw) {
        rejected.increment();
        log.warn("Lectura rechazada de {}: {}", nodeCode, reason);
        invalid.save(nodeCode, reason, raw);
        return Outcome.INVALID;
    }
}
