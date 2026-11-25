package com.sensonet.readings;

import com.sensonet.devices.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Lecturas")
public class ReadingController {

    private final ReadingRepository readings;
    private final NodeService nodes;
    private final Clock clock;

    public ReadingController(ReadingRepository readings, NodeService nodes, Clock clock) {
        this.readings = readings;
        this.nodes = nodes;
        this.clock = clock;
    }

    public record SeriesResponse(long nodeId, Instant from, Instant to, String bucket, List<ReadingPoint> points) {}

    @Operation(summary = "Histórico de un nodo, agregado por intervalo")
    @GetMapping("/api/nodes/{id}/readings")
    public SeriesResponse series(
            @PathVariable long id,
            @Parameter(description = "Inicio ISO-8601 (por defecto: hace 24 h)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fin ISO-8601 (por defecto: ahora)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "raw, 1m, 1h, 1d (por defecto: según el rango)")
            @RequestParam(required = false) String bucket) {
        nodes.get(id);
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from debe ser anterior a to");
        }
        Duration range = Duration.between(start, end);
        Bucket b = bucket == null ? Bucket.auto(range) : Bucket.fromCode(bucket);
        if (range.compareTo(b.maxRange()) > 0) {
            throw new IllegalArgumentException("Rango demasiado grande para bucket " + b.name().toLowerCase()
                    + " (máximo " + b.maxRange().toDays() + " días)");
        }
        return new SeriesResponse(id, start, end, b.name().toLowerCase(), readings.series(id, start, end, b));
    }
}
