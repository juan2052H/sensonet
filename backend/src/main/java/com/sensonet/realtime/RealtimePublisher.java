package com.sensonet.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Envía eventos al dashboard por STOMP.
 * <ul>
 *   <li>{@code /topic/nodes/{id}}: lecturas nuevas de un nodo</li>
 *   <li>{@code /topic/nodes}: cambios de estado de cualquier nodo</li>
 *   <li>{@code /topic/alerts}: alertas abiertas, cerradas o reconocidas</li>
 *   <li>{@code /topic/commands}: cambios de estado de comandos</li>
 * </ul>
 */
@Component
public class RealtimePublisher {

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void reading(long nodeId, Object reading) {
        template.convertAndSend("/topic/nodes/" + nodeId, reading);
    }

    public void nodeUpdated(Object node) {
        template.convertAndSend("/topic/nodes", node);
    }

    public void alert(Object alert) {
        template.convertAndSend("/topic/alerts", alert);
    }

    public void command(Object command) {
        template.convertAndSend("/topic/commands", command);
    }
}
