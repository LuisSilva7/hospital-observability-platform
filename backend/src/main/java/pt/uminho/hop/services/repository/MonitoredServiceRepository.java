package pt.uminho.hop.services.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.services.domain.MonitoredService;

import java.util.UUID;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
