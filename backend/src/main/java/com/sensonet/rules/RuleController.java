package com.sensonet.rules;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reglas de alerta")
public class RuleController {

    private final RuleService rules;
    private final RulePreviewService preview;
    private final Clock clock;

    public RuleController(RuleService rules, RulePreviewService preview, Clock clock) {
        this.rules = rules;
        this.preview = preview;
        this.clock = clock;
    }

    public record RuleRequest(
            Long nodeId,
            @NotBlank @Size(max = 80) String name,
            @NotNull Metric metric,
            @NotNull Operator operator,
            @NotNull Double threshold,
            @NotNull Double recovery,
            @NotNull @Min(0) @Max(86_400) Integer durationSeconds,
            @NotNull Severity severity,
            Boolean enabled) {

        RuleService.RuleInput toInput() {
            return new RuleService.RuleInput(nodeId, name, metric, operator, threshold, recovery, durationSeconds,
                    severity, enabled == null || enabled);
        }
    }

    public record RuleDto(Long id, Long nodeId, String name, Metric metric, String unit, Operator operator,
                          double threshold, double recovery, int durationSeconds, Severity severity,
                          boolean enabled, Instant createdAt) {
        static RuleDto of(AlertRule r) {
            return new RuleDto(r.getId(), r.getNodeId(), r.getName(), r.getMetric(), r.getMetric().unit(),
                    r.getOperator(), r.getThreshold(), r.getRecovery(), r.getDurationSeconds(), r.getSeverity(),
                    r.isEnabled(), r.getCreatedAt());
        }
    }

    public record PreviewRequest(
            @NotNull Long nodeId,
            @NotNull Metric metric,
            @NotNull Operator operator,
            @NotNull Double threshold,
            @NotNull Double recovery,
            @NotNull @Min(0) @Max(86_400) Integer durationSeconds,
            Instant from,
            Instant to) {}

    @Operation(summary = "Lista de reglas")
    @GetMapping("/api/rules")
    public List<RuleDto> list() {
        return rules.list().stream().map(RuleDto::of).toList();
    }

    @Operation(summary = "Detalle de una regla")
    @GetMapping("/api/rules/{id}")
    public RuleDto get(@PathVariable long id) {
        return RuleDto.of(rules.get(id));
    }

    @Operation(summary = "Crea una regla (ADMIN)")
    @PostMapping("/api/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleDto create(@Valid @RequestBody RuleRequest req) {
        return RuleDto.of(rules.create(req.toInput()));
    }

    @Operation(summary = "Modifica una regla (ADMIN); cierra sus alertas abiertas")
    @PutMapping("/api/rules/{id}")
    public RuleDto update(@PathVariable long id, @Valid @RequestBody RuleRequest req) {
        return RuleDto.of(rules.update(id, req.toInput()));
    }

    @Operation(summary = "Elimina una regla (ADMIN)")
    @DeleteMapping("/api/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        rules.delete(id);
    }

    @Operation(summary = "Vista previa: cuántas veces se habría disparado la regla en el histórico (ADMIN)")
    @PostMapping("/api/rules/preview")
    public RulePreviewService.Result preview(@Valid @RequestBody PreviewRequest req) {
        Instant to = req.to() != null ? req.to() : clock.instant();
        Instant from = req.from() != null ? req.from() : to.minus(Duration.ofHours(24));
        if (Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("La vista previa admite como máximo 7 días");
        }
        RuleSpec spec = new RuleSpec(req.metric(), req.operator(), req.threshold(), req.recovery(),
                Duration.ofSeconds(req.durationSeconds()));
        return preview.preview(spec, req.nodeId(), from, to);
    }
}
