package pt.uminho.hop.ingest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pt.uminho.hop.ingest.domain.LogEvent;

import java.util.List;
import java.util.UUID;

public interface LogEventRepository
        extends JpaRepository<LogEvent, UUID>, JpaSpecificationExecutor<LogEvent> {
    List<LogEvent> findTop20ByServiceIdOrderByReceivedAtDesc(UUID serviceId);

    /** Timestamps dos logs de erro de um serviço desde `from` (para o detetor de anomalias). */
    @org.springframework.data.jpa.repository.Query("""
            select e.receivedAt from LogEvent e
            where e.serviceId = :serviceId
              and e.level in ('ERROR', 'FATAL')
              and e.receivedAt >= :from
            """)
    List<java.time.OffsetDateTime> findErrorTimestampsSince(UUID serviceId, java.time.OffsetDateTime from);

    /** Apaga logs antigos, preservando os associados a alertas (evidência). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            delete from LogEvent e
            where e.receivedAt < :cutoff
              and e.id not in (select l.logEventId from AlertLogLink l)
            """)
    int deleteUnlinkedOlderThan(java.time.OffsetDateTime cutoff);
}
