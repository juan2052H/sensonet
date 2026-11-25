package com.sensonet.commands;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commands")
public class Command {

    @Id
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CommandStatus status = CommandStatus.PENDING;

    @Column(length = 200)
    private String error;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    protected Command() {}

    public Command(UUID id, Long nodeId, String type, String payload, String createdBy, Instant createdAt) {
        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.payload = payload;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void confirm(Instant at) {
        status = CommandStatus.CONFIRMED;
        ackedAt = at;
    }

    public void fail(String reason, Instant at) {
        status = CommandStatus.FAILED;
        error = reason;
        if (at != null) ackedAt = at;
    }

    public UUID getId() { return id; }
    public Long getNodeId() { return nodeId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public CommandStatus getStatus() { return status; }
    public String getError() { return error; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAckedAt() { return ackedAt; }
}
