package pt.uminho.hop.alerts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.alerts.domain.AlertLogLink;

import java.util.List;
import java.util.UUID;

public interface AlertLogLinkRepository extends JpaRepository<AlertLogLink, AlertLogLink.Key> {
    List<AlertLogLink> findByAlertId(UUID alertId);
    List<AlertLogLink> findByAlertIdIn(List<UUID> alertIds);
}
