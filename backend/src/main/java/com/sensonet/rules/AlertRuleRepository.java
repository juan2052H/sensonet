package com.sensonet.rules;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findAllByOrderByIdAsc();
}
