package pt.uminho.hop.automations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.automations.domain.Automation;

import java.util.List;
import java.util.UUID;

public interface AutomationRepository extends JpaRepository<Automation, UUID> {
    List<Automation> findByRuleIdAndEnabledTrue(UUID ruleId);
}
