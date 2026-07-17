package pt.uminho.hop.automations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.automations.domain.ActionExecution;

import java.util.List;
import java.util.UUID;

public interface ActionExecutionRepository extends JpaRepository<ActionExecution, UUID> {
    List<ActionExecution> findByAlertIdOrderByExecutedAtDesc(UUID alertId);
    List<ActionExecution> findByAlertIdIn(List<UUID> alertIds);
    List<ActionExecution> findTop50ByActionIdInOrderByExecutedAtDesc(List<UUID> actionIds);
    long countByExecutedAtGreaterThanEqual(java.time.OffsetDateTime from);
}
