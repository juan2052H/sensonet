package com.sensonet.commands;

import java.time.Instant;
import java.util.UUID;

public record CommandDto(UUID id, Long nodeId, String type, String payload, CommandStatus status, String error,
                         String createdBy, Instant createdAt, Instant ackedAt) {

    public static CommandDto of(Command c) {
        return new CommandDto(c.getId(), c.getNodeId(), c.getType(), c.getPayload(), c.getStatus(), c.getError(),
                c.getCreatedBy(), c.getCreatedAt(), c.getAckedAt());
    }
}
