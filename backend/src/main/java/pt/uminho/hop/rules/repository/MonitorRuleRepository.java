package pt.uminho.hop.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.rules.domain.MonitorRule;

import java.util.List;
import java.util.UUID;

public interface MonitorRuleRepository extends JpaRepository<MonitorRule, UUID> {
    List<MonitorRule> findByServiceIdAndEnabledTrue(UUID serviceId);
    List<MonitorRule> findByTypeAndEnabledTrue(MonitorRule.Type type);
}
