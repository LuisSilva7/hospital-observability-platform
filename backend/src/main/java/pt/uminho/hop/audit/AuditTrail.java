package pt.uminho.hop.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pt.uminho.hop.audit.domain.AuditEntry;
import pt.uminho.hop.audit.repository.AuditEntryRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Regista entradas de auditoria (módulo 9). Sem autenticação/multi-utilizador
 * no MVP, o ator distingue apenas ações humanas via API (USER) de ações do
 * próprio sistema (SYSTEM: motor de regras, executor de automações).
 * Nunca registar segredos (API keys, tokens) nos detalhes.
 *
 * Falha-segura: cada entrada é escrita numa transação PRÓPRIA (REQUIRES_NEW via
 * TransactionTemplate) e qualquer erro — incluindo o commit dessa transação — é
 * capturado aqui, para nunca marcar a transação de negócio como rollback-only
 * nem partir a operação principal.
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private final AuditEntryRepository entries;
    private final ObjectMapper mapper;
    private final TransactionTemplate newTransaction;

    public AuditTrail(AuditEntryRepository entries, ObjectMapper mapper,
                      PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.mapper = mapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void user(String action, String entityType, UUID entityId, Map<String, ?> details) {
        record(AuditEntry.Actor.USER, action, entityType, entityId, details);
    }

    public void system(String action, String entityType, UUID entityId, Map<String, ?> details) {
        record(AuditEntry.Actor.SYSTEM, action, entityType, entityId, details);
    }

    private void record(AuditEntry.Actor actor, String action, String entityType,
                        UUID entityId, Map<String, ?> details) {
        try {
            AuditEntry entry = new AuditEntry();
            entry.setActor(actor);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            if (details != null && !details.isEmpty()) {
                entry.setDetails(mapper.writeValueAsString(details));
            }
            newTransaction.executeWithoutResult(status -> entries.save(entry));
        } catch (Exception e) {
            // auditoria nunca pode partir a operação principal
            log.error("Falha a registar auditoria {} {}: {}", action, entityId, e.getMessage());
        }
    }
}
