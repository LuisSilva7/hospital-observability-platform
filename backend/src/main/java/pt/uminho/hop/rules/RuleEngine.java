package pt.uminho.hop.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.domain.RuleEvaluation;
import pt.uminho.hop.rules.repository.MonitorRuleRepository;
import pt.uminho.hop.rules.repository.RuleEvaluationRepository;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final MonitorRuleRepository rules;
    private final RuleEvaluationRepository evaluations;
    private final MonitoredServiceRepository services;
    private final LogEventRepository logEvents;
    private final List<RuleTriggerHandler> triggerHandlers;

    public RuleEngine(MonitorRuleRepository rules,
                      RuleEvaluationRepository evaluations,
                      MonitoredServiceRepository services,
                      LogEventRepository logEvents,
                      List<RuleTriggerHandler> triggerHandlers) {
        this.rules = rules;
        this.evaluations = evaluations;
        this.services = services;
        this.logEvents = logEvents;
        this.triggerHandlers = triggerHandlers;
    }

    /** Chamado após cada ingestão: avalia EVENT_MATCH e COUNT_THRESHOLD do serviço. */
    @Transactional
    public void evaluateOnIngest(UUID serviceId, UUID logEventId, JsonNode payload) {
        OffsetDateTime now = OffsetDateTime.now();
        for (MonitorRule rule : rules.findByServiceIdAndEnabledTrue(serviceId)) {
            try {
                switch (rule.getType()) {
                    case EVENT_MATCH -> evaluateEventMatch(rule, logEventId, payload, now);
                    case COUNT_THRESHOLD -> evaluateCountThreshold(rule, logEventId, payload, now);
                    case NO_ACTIVITY, ANOMALY -> { /* avaliadas pelos jobs agendados */ }
                }
            } catch (Exception e) {
                // uma regra partida não pode impedir a ingestão nem as outras regras
                log.error("Erro ao avaliar regra {} ({})", rule.getName(), rule.getId(), e);
            }
        }
    }

    private void evaluateEventMatch(MonitorRule rule, UUID logEventId, JsonNode payload, OffsetDateTime now) {
        if (inCooldown(rule, now)) return;
        boolean allMatch = !rule.getConditions().isEmpty() && rule.getConditions().stream()
                .allMatch(c -> ConditionMatcher.matches(c, payload));
        if (allMatch) {
            trigger(rule, logEventId, "Log corresponde às condições da regra", now);
        }
    }

    private void evaluateCountThreshold(MonitorRule rule, UUID logEventId, JsonNode payload, OffsetDateTime now) {
        if (inCooldown(rule, now)) return;
        if (rule.getConditions().isEmpty()
                || rule.getConditions().stream().noneMatch(c -> ConditionMatcher.matches(c, payload))) {
            // o log atual não corresponde — não vale a pena contar
            return;
        }
        int window = rule.getWindowMinutes() == null ? 15 : rule.getWindowMinutes();
        int threshold = rule.getThreshold() == null ? 5 : rule.getThreshold();
        OffsetDateTime from = now.minusMinutes(window);

        // Conta na janela apenas pelos campos normalizados indexáveis; a
        // verificação fina (condições) já foi feita no log atual.
        long count = countMatchingInWindow(rule, from);
        if (count >= threshold) {
            trigger(rule, logEventId,
                    "%d eventos correspondentes nos últimos %d minutos (limite: %d)"
                            .formatted(count, window, threshold), now);
        }
    }

    private long countMatchingInWindow(MonitorRule rule, OffsetDateTime from) {
        Specification<pt.uminho.hop.ingest.domain.LogEvent> spec =
                (r, q, cb) -> cb.and(
                        cb.equal(r.get("serviceId"), rule.getServiceId()),
                        cb.greaterThanOrEqualTo(r.get("receivedAt"), from));

        // Se houver condição simples sobre "level", usa-a para contar com precisão
        var levelCondition = rule.getConditions().stream()
                .filter(c -> c.getFieldPath().equals("level")
                        && c.getOperator() == pt.uminho.hop.rules.domain.RuleCondition.Operator.EQUALS)
                .findFirst();
        if (levelCondition.isPresent()) {
            String level = levelCondition.get().getExpectedValue().toUpperCase();
            spec = spec.and((r, q, cb) -> cb.equal(r.get("level"), level));
        }
        return logEvents.count(spec);
    }

    /** Job agendado: regras NO_ACTIVITY (serviço sem logs há mais de windowMinutes). */
    @Scheduled(fixedDelayString = "${hop.rules.no-activity-check-seconds:30}000")
    @Transactional
    public void checkNoActivity() {
        checkNoActivity(OffsetDateTime.now());
    }

    void checkNoActivity(OffsetDateTime now) {
        for (MonitorRule rule : rules.findByTypeAndEnabledTrue(MonitorRule.Type.NO_ACTIVITY)) {
            try {
                if (inCooldown(rule, now)) continue;
                MonitoredService service = services.findById(rule.getServiceId()).orElse(null);
                if (service == null || !service.isActive()) continue;

                int window = rule.getWindowMinutes() == null ? 10 : rule.getWindowMinutes();
                OffsetDateTime reference = service.getLastSeenAt() == null
                        ? service.getCreatedAt()
                        : service.getLastSeenAt();
                if (reference.plusMinutes(window).isBefore(now)) {
                    trigger(rule, null,
                            "Sem logs desde %s (janela: %d minutos)".formatted(reference, window), now);
                }
            } catch (Exception e) {
                log.error("Erro ao avaliar regra NO_ACTIVITY {} ({})", rule.getName(), rule.getId(), e);
            }
        }
    }

    /** Número de janelas anteriores usadas como histórico do z-score. */
    static final int ANOMALY_HISTORY_BUCKETS = 12;

    /** Job agendado: regras ANOMALY (z-score da taxa de erros face ao histórico). */
    @Scheduled(fixedDelayString = "${hop.rules.anomaly-check-seconds:60}000")
    @Transactional
    public void checkAnomalies() {
        checkAnomalies(OffsetDateTime.now());
    }

    void checkAnomalies(OffsetDateTime now) {
        for (MonitorRule rule : rules.findByTypeAndEnabledTrue(MonitorRule.Type.ANOMALY)) {
            try {
                if (inCooldown(rule, now)) continue;
                MonitoredService service = services.findById(rule.getServiceId()).orElse(null);
                if (service == null || !service.isActive()) continue;

                int window = rule.getWindowMinutes() == null ? 15 : rule.getWindowMinutes();
                double minZ = rule.getThreshold() == null ? 3 : rule.getThreshold();
                OffsetDateTime historyStart =
                        now.minusMinutes((long) window * (ANOMALY_HISTORY_BUCKETS + 1));

                // uma só query: timestamps dos erros; janelas calculadas em memória
                List<OffsetDateTime> errors =
                        logEvents.findErrorTimestampsSince(rule.getServiceId(), historyStart);
                long[] buckets = new long[ANOMALY_HISTORY_BUCKETS + 1];
                for (OffsetDateTime ts : errors) {
                    long minutesAgo = java.time.Duration.between(ts, now).toMinutes();
                    int bucket = (int) (minutesAgo / window);
                    if (bucket >= 0 && bucket < buckets.length) buckets[bucket]++;
                }
                long current = buckets[0];
                List<Long> history = java.util.Arrays.stream(buckets).skip(1).boxed().toList();

                AnomalyDetector.detect(history, current, minZ).ifPresent(result ->
                        trigger(rule, null,
                                ("Taxa de erros anómala: %d erros nos últimos %d min "
                                        + "(média histórica %.1f, desvio %.1f, z=%.1f ≥ %.0f)")
                                        .formatted(result.current(), window, result.mean(),
                                                result.stdDev(), result.zScore(), minZ), now));
            } catch (Exception e) {
                log.error("Erro ao avaliar regra ANOMALY {} ({})", rule.getName(), rule.getId(), e);
            }
        }
    }

    private boolean inCooldown(MonitorRule rule, OffsetDateTime now) {
        return rule.getLastTriggeredAt() != null
                && rule.getLastTriggeredAt().plusMinutes(rule.getCooldownMinutes()).isAfter(now);
    }

    private void trigger(MonitorRule rule, UUID logEventId, String details, OffsetDateTime now) {
        rule.setLastTriggeredAt(now);
        rules.save(rule);

        RuleEvaluation evaluation = new RuleEvaluation();
        evaluation.setRuleId(rule.getId());
        evaluation.setServiceId(rule.getServiceId());
        evaluation.setLogEventId(logEventId);
        evaluation.setDetails(details);
        evaluations.save(evaluation);

        // handlers registados: AlertManager (M6); automações chegam no M7
        for (RuleTriggerHandler handler : triggerHandlers) {
            try {
                handler.onRuleTriggered(rule, logEventId, details);
            } catch (Exception e) {
                log.error("Handler {} falhou para a regra {}", handler.getClass().getSimpleName(), rule.getId(), e);
            }
        }
        log.info("Regra disparada: '{}' ({}) — {}", rule.getName(), rule.getType(), details);
    }
}
