package com.sensonet.rules;

import com.sensonet.common.NotFoundException;
import com.sensonet.devices.NodeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {

    private final AlertRuleRepository repo;
    private final RuleEvaluationService evaluation;
    private final NodeService nodes;

    public RuleService(AlertRuleRepository repo, RuleEvaluationService evaluation, NodeService nodes) {
        this.repo = repo;
        this.evaluation = evaluation;
        this.nodes = nodes;
    }

    public record RuleInput(Long nodeId, String name, Metric metric, Operator operator, double threshold,
                            double recovery, int durationSeconds, Severity severity, boolean enabled) {}

    @Transactional(readOnly = true)
    public List<AlertRule> list() {
        return repo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public AlertRule get(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Regla " + id + " no encontrada"));
    }

    @Transactional
    public AlertRule create(RuleInput in) {
        if (in.nodeId() != null) nodes.get(in.nodeId());
        AlertRule rule = repo.save(new AlertRule(in.nodeId(), in.name().trim(), in.metric(), in.operator(),
                in.threshold(), in.recovery(), in.durationSeconds(), in.severity(), in.enabled()));
        evaluation.reloadRules();
        return rule;
    }

    @Transactional
    public AlertRule update(long id, RuleInput in) {
        if (in.nodeId() != null) nodes.get(in.nodeId());
        AlertRule rule = get(id);
        rule.apply(in.nodeId(), in.name().trim(), in.metric(), in.operator(), in.threshold(), in.recovery(),
                in.durationSeconds(), in.severity(), in.enabled());
        repo.saveAndFlush(rule);
        evaluation.onRuleChanged(id);
        return rule;
    }

    @Transactional
    public void delete(long id) {
        AlertRule rule = get(id);
        evaluation.onRuleChanged(id);
        repo.delete(rule);
        repo.flush();
        evaluation.reloadRules();
    }
}
