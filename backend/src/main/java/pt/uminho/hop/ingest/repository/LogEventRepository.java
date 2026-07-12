package pt.uminho.hop.ingest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.ingest.domain.LogEvent;

import java.util.List;
import java.util.UUID;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
    List<LogEvent> findTop20ByServiceIdOrderByReceivedAtDesc(UUID serviceId);
}
