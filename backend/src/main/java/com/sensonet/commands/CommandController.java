package com.sensonet.commands;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Comandos")
public class CommandController {

    private final CommandService commands;

    public CommandController(CommandService commands) {
        this.commands = commands;
    }

    public record RelayRequest(@NotNull Boolean on) {}

    @Operation(summary = "Enciende o apaga el relé del nodo (ADMIN). Devuelve el comando en estado PENDING")
    @PostMapping("/api/nodes/{id}/commands/relay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandDto relay(@PathVariable long id, @Valid @RequestBody RelayRequest req,
                            @AuthenticationPrincipal Jwt principal) {
        return commands.sendRelay(id, req.on(), principal.getSubject());
    }

    @Operation(summary = "Estado de un comando")
    @GetMapping("/api/commands/{id}")
    public CommandDto get(@PathVariable UUID id) {
        return commands.get(id);
    }

    @Operation(summary = "Últimos comandos de un nodo")
    @GetMapping("/api/nodes/{id}/commands")
    public List<CommandDto> recent(@PathVariable long id, @RequestParam(defaultValue = "10") int limit) {
        return commands.recent(id, limit);
    }
}
