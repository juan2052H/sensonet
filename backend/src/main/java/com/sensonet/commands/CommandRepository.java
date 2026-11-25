package com.sensonet.commands;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandRepository extends JpaRepository<Command, UUID> {
    List<Command> findByStatusAndCreatedAtBefore(CommandStatus status, Instant before);

    List<Command> findByNodeIdOrderByCreatedAtDesc(Long nodeId, Pageable page);
}
