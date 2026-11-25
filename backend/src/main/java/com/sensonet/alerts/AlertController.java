package com.sensonet.alerts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Alertas")
public class AlertController {

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @Operation(summary = "Alertas abiertas, cerradas o todas")
    @GetMapping("/api/alerts")
    public List<AlertDto> list(@RequestParam(defaultValue = "ALL") String status,
                               @RequestParam(required = false) Long nodeId,
                               @RequestParam(defaultValue = "100") int limit) {
        return alerts.list(status, nodeId, limit);
    }

    @Operation(summary = "Reconocer una alerta (ADMIN)")
    @PostMapping("/api/alerts/{id}/ack")
    public AlertDto ack(@PathVariable long id, @AuthenticationPrincipal Jwt principal) {
        return alerts.ack(id, principal.getSubject());
    }
}
