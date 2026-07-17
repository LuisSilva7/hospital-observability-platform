package pt.uminho.hop.automations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.automations.domain.AutomationAction;

import java.util.UUID;

public interface AutomationActionRepository extends JpaRepository<AutomationAction, UUID> {
}
