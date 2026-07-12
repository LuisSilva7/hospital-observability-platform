package pt.uminho.hop.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.rules.domain.RuleEvaluation;

import java.util.List;
import java.util.UUID;

public interface RuleEvaluationRepository extends JpaRepository<RuleEvaluation, UUID> {
    List<RuleEvaluation> findTop50ByRuleIdOrderByTriggeredAtDesc(UUID ruleId);
}
