package com.sensonet.devices;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, Long> {
    Optional<Node> findByCode(String code);

    List<Node> findAllByOrderByNameAsc();

    List<Node> findByOnlineTrueAndLastSeenBefore(Instant threshold);
}
