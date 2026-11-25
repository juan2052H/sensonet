package com.sensonet.devices;

import com.sensonet.ingestion.MqttGateway;
import com.sensonet.ingestion.MqttTopics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Nodos")
public class NodeController {

    private final NodeService nodes;
    private final MqttGateway mqtt;

    public NodeController(NodeService nodes, MqttGateway mqtt) {
        this.nodes = nodes;
        this.mqtt = mqtt;
    }

    public record UpdateNodeRequest(@Size(max = 80) String name, @Size(max = 120) String location) {}

    public record ConfigRequest(@NotNull @Min(5) @Max(3600) Integer intervalSeconds) {}

    @Operation(summary = "Lista de nodos con estado y última lectura")
    @GetMapping("/api/nodes")
    public List<NodeDto> list() {
        return nodes.list().stream().map(NodeDto::of).toList();
    }

    @Operation(summary = "Detalle de un nodo")
    @GetMapping("/api/nodes/{id}")
    public NodeDto get(@PathVariable long id) {
        return NodeDto.of(nodes.get(id));
    }

    @Operation(summary = "Cambia nombre y ubicación (ADMIN)")
    @PutMapping("/api/nodes/{id}")
    public NodeDto update(@PathVariable long id, @Valid @RequestBody UpdateNodeRequest req) {
        return NodeDto.of(nodes.update(id, req.name(), req.location()));
    }

    @Operation(summary = "Cambia el intervalo de muestreo (ADMIN). Se publica retenido en sensonet/v1/{nodo}/config")
    @PutMapping("/api/nodes/{id}/config")
    public NodeDto config(@PathVariable long id, @Valid @RequestBody ConfigRequest req) {
        Node node = nodes.get(id);
        mqtt.publish(MqttTopics.config(node.getCode()), "{\"interval\":" + req.intervalSeconds() + "}", 1, true);
        return NodeDto.of(nodes.setInterval(id, req.intervalSeconds()));
    }
}
