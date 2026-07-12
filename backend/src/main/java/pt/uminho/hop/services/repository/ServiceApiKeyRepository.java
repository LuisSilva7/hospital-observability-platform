package pt.uminho.hop.services.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.services.domain.ServiceApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceApiKeyRepository extends JpaRepository<ServiceApiKey, UUID> {
    Optional<ServiceApiKey> findByKeyHashAndActiveTrue(String keyHash);
    List<ServiceApiKey> findByServiceIdAndActiveTrue(UUID serviceId);
    Optional<ServiceApiKey> findFirstByServiceIdAndActiveTrueOrderByCreatedAtDesc(UUID serviceId);
}
