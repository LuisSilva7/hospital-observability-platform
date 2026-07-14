package pt.uminho.hop.alerts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.alerts.domain.AlertEvent;

import java.util.List;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    List<AlertEvent> findByAlertIdOrderByCreatedAtAsc(UUID alertId);
}
