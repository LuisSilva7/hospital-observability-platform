package pt.uminho.hop.alerts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.alerts.domain.Alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Optional<Alert> findFirstByRuleIdAndStatusNotOrderByOpenedAtDesc(UUID ruleId, Alert.Status status);
    List<Alert> findByStatusInOrderByOpenedAtDesc(List<Alert.Status> statuses);
    List<Alert> findAllByOrderByOpenedAtDesc();
    long countByStatusIn(List<Alert.Status> statuses);
}
