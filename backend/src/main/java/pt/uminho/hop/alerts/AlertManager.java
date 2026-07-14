package pt.uminho.hop.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.domain.AlertEvent;
import pt.uminho.hop.alerts.domain.AlertLogLink;
import pt.uminho.hop.alerts.repository.AlertEventRepository;
import pt.uminho.hop.alerts.repository.AlertLogLinkRepository;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.common.ConflictException;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.rules.RuleTriggerHandler;
import pt.uminho.hop.rules.domain.MonitorRule;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cria e gere alertas. Deduplicação: enquanto existir um alerta não resolvido
 * para a mesma regra, novos disparos são anexados à timeline desse alerta em
 * vez de criarem um alerta novo.
 */
@Component
public class AlertManager implements RuleTriggerHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final AlertRepository alerts;
    private final AlertEventRepository events;
    private final AlertLogLinkRepository logLinks;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public AlertManager(AlertRepository alerts,
                        AlertEventRepository events,
                        AlertLogLinkRepository logLinks,
                        org.springframework.context.ApplicationEventPublisher publisher) {
        this.alerts = alerts;
        this.events = events;
        this.logLinks = logLinks;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public void onRuleTriggered(MonitorRule rule, UUID logEventId, String details) {
        var existing = alerts.findFirstByRuleIdAndStatusNotOrderByOpenedAtDesc(
                rule.getId(), Alert.Status.RESOLVED);

        if (existing.isPresent()) {
            Alert alert = existing.get();
            events.save(AlertEvent.of(alert.getId(), AlertEvent.Type.TRIGGER_REPEATED, details));
            linkLog(alert.getId(), logEventId);
            log.info("Disparo anexado ao alerta existente {}", alert.getId());
            return;
        }

        Alert alert = new Alert();
        alert.setServiceId(rule.getServiceId());
        alert.setRuleId(rule.getId());
        alert.setTitle(rule.getName());
        alert.setSeverity(rule.getSeverity());
        alert = alerts.save(alert);
        events.save(AlertEvent.of(alert.getId(), AlertEvent.Type.CREATED,
                "Alerta criado pela regra '" + rule.getName() + "': " + details));
        linkLog(alert.getId(), logEventId);
        // consumido pelo AutomationExecutor (M7) após o commit da transação
        publisher.publishEvent(new pt.uminho.hop.automations.AlertCreatedEvent(alert.getId(), rule.getId()));
        log.info("Alerta criado: {} ({})", alert.getTitle(), alert.getId());
    }

    private void linkLog(UUID alertId, UUID logEventId) {
        if (logEventId != null) {
            logLinks.save(new AlertLogLink(alertId, logEventId));
        }
    }

    @Transactional
    public Alert acknowledge(UUID alertId) {
        Alert alert = find(alertId);
        if (alert.getStatus() != Alert.Status.OPEN) {
            throw new ConflictException("Só alertas OPEN podem ser reconhecidos");
        }
        alert.setStatus(Alert.Status.ACKNOWLEDGED);
        alert.setAcknowledgedAt(OffsetDateTime.now());
        events.save(AlertEvent.of(alertId, AlertEvent.Type.ACKNOWLEDGED, "Alerta reconhecido pelo operador"));
        return alerts.save(alert);
    }

    @Transactional
    public Alert resolve(UUID alertId) {
        Alert alert = find(alertId);
        if (alert.getStatus() == Alert.Status.RESOLVED) {
            throw new ConflictException("O alerta já está resolvido");
        }
        alert.setStatus(Alert.Status.RESOLVED);
        alert.setResolvedAt(OffsetDateTime.now());
        events.save(AlertEvent.of(alertId, AlertEvent.Type.RESOLVED, "Alerta resolvido pelo operador"));
        return alerts.save(alert);
    }

    private Alert find(UUID id) {
        return alerts.findById(id).orElseThrow(() -> new NotFoundException("Alerta não encontrado"));
    }
}
