package pt.uminho.hop.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.audit.domain.AuditEntry;

import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
    Page<AuditEntry> findByEntityType(String entityType, Pageable pageable);
}
