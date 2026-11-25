package com.sensonet.rules;

import com.sensonet.readings.ReadingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * "Esta regla se habría disparado 3 veces ayer": ejecuta el mismo motor de reglas sobre el
 * histórico crudo, sin crear alertas.
 */
@Service
public class RulePreviewService {

    private final ReadingRepository readings;

    public RulePreviewService(ReadingRepository readings) {
        this.readings = readings;
    }

    public record Interval(Instant openedAt, Instant closedAt, double peak) {}

    public record Result(long samples, int openings, List<Interval> intervals) {}

    public Result preview(RuleSpec spec, long nodeId, Instant from, Instant to) {
        RuleState state = new RuleState();
        List<Interval> intervals = new ArrayList<>();
        long[] samples = {0};
        Instant[] openedAt = {null};
        double[] peak = {0};

        readings.forEachRaw(nodeId, from, to, r -> {
            Double v = r.value(spec.metric());
            if (v == null) return;
            samples[0]++;
            switch (RuleEngine.evaluate(spec, state, v, r.ts())) {
                case OPEN -> {
                    openedAt[0] = r.ts();
                    peak[0] = v;
                }
                case CLOSE -> {
                    intervals.add(new Interval(openedAt[0], r.ts(), peak[0]));
                    openedAt[0] = null;
                }
                case NONE -> {
                    if (openedAt[0] != null && spec.operator().moreExtreme(v, peak[0])) peak[0] = v;
                }
            }
        });
        if (openedAt[0] != null) {
            intervals.add(new Interval(openedAt[0], null, peak[0]));
        }
        return new Result(samples[0], intervals.size(), intervals);
    }
}
