package pt.uminho.hop.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uminho.hop.ai.domain.AIAnalysis;

import java.util.List;
import java.util.UUID;

public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, UUID> {
    List<AIAnalysis> findByAlertIdOrderByCreatedAtDesc(UUID alertId);
}
