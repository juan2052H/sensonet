package com.sensonet.alerts;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByClosedAtIsNullOrderByOpenedAtDesc();

    List<Alert> findByClosedAtIsNotNullOrderByOpenedAtDesc(Pageable page);

    List<Alert> findAllByOrderByOpenedAtDesc(Pageable page);

    List<Alert> findByRuleIdAndClosedAtIsNull(Long ruleId);
}
