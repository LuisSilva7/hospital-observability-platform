package pt.uminho.hop.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.uminho.hop.ai.repository.AIAnalysisRepository;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.repository.AlertLogLinkRepository;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.automations.domain.ActionExecution;
import pt.uminho.hop.automations.repository.ActionExecutionRepository;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Métricas de avaliação do protótipo (dissertação, método DSR): tempos de
 * deteção e notificação, MTTA e MTTR, derivados dos timestamps já persistidos.
 * Sem estado próprio — tudo é calculado a partir de alert/log_event/action_execution.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final AlertRepository alerts;
    private final AlertLogLinkRepository logLinks;
    private final LogEventRepository logEvents;
    private final ActionExecutionRepository executions;
    private final AIAnalysisRepository analyses;

    public MetricsController(AlertRepository alerts,
                             AlertLogLinkRepository logLinks,
                             LogEventRepository logEvents,
                             ActionExecutionRepository executions,
                             AIAnalysisRepository analyses) {
        this.alerts = alerts;
        this.logLinks = logLinks;
        this.logEvents = logEvents;
        this.executions = executions;
        this.analyses = analyses;
    }

    /** Estatística de uma lista de durações em milissegundos. */
    public record Stat(long count, Long avgMs, Long p50Ms, Long p95Ms, Long maxMs) {

        static Stat of(List<Long> durations) {
            if (durations.isEmpty()) {
                return new Stat(0, null, null, null, null);
            }
            List<Long> sorted = durations.stream().sorted().toList();
            long avg = Math.round(sorted.stream().mapToLong(Long::longValue).average().orElse(0));
            return new Stat(sorted.size(), avg,
                    percentile(sorted, 50), percentile(sorted, 95),
                    sorted.get(sorted.size() - 1));
        }

        private static long percentile(List<Long> sorted, int p) {
            int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
        }
    }

    public record Counts(long alerts, long openAlerts, long acknowledgedAlerts, long resolvedAlerts,
                         long logs, long actionExecutions, long aiAnalyses) {}

    public record MetricsResponse(
            Integer windowDays, Counts counts,
            Stat detection, Stat notification, Stat mtta, Stat mttr) {}

    @GetMapping
    public MetricsResponse get(@RequestParam(required = false) Integer days) {
        boolean windowed = days != null && days > 0;
        OffsetDateTime from = windowed ? OffsetDateTime.now().minusDays(days) : null;

        // janela empurrada para SQL (sem findAll() nem filtro em memória)
        List<Alert> windowAlerts = from == null
                ? alerts.findAll()
                : alerts.findByOpenedAtGreaterThanEqual(from);
        List<UUID> alertIds = windowAlerts.stream().map(Alert::getId).toList();

        // 1.º log recebido por alerta (deteção), em lote: links → logs → min(receivedAt)
        Map<UUID, OffsetDateTime> firstLogByAlert = new java.util.HashMap<>();
        if (!alertIds.isEmpty()) {
            List<pt.uminho.hop.alerts.domain.AlertLogLink> links = logLinks.findByAlertIdIn(alertIds);
            Map<UUID, OffsetDateTime> receivedById = logEvents.findAllById(
                            links.stream().map(l -> l.getLogEventId()).distinct().toList()).stream()
                    .collect(Collectors.toMap(LogEvent::getId, LogEvent::getReceivedAt));
            for (var link : links) {
                OffsetDateTime received = receivedById.get(link.getLogEventId());
                if (received != null) {
                    firstLogByAlert.merge(link.getAlertId(), received,
                            (a, b) -> a.isBefore(b) ? a : b);
                }
            }
        }

        // 1.ª execução por alerta (notificação), em lote
        Map<UUID, OffsetDateTime> firstExecByAlert = new java.util.HashMap<>();
        if (!alertIds.isEmpty()) {
            for (ActionExecution e : executions.findByAlertIdIn(alertIds)) {
                firstExecByAlert.merge(e.getAlertId(), e.getExecutedAt(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        List<Long> detection = new ArrayList<>();
        List<Long> notification = new ArrayList<>();
        List<Long> mtta = new ArrayList<>();
        List<Long> mttr = new ArrayList<>();

        for (Alert alert : windowAlerts) {
            OffsetDateTime firstLog = firstLogByAlert.get(alert.getId());
            if (firstLog != null) {
                long ms = Duration.between(firstLog, alert.getOpenedAt()).toMillis();
                if (ms >= 0) detection.add(ms);
            }
            OffsetDateTime firstExec = firstExecByAlert.get(alert.getId());
            if (firstExec != null) {
                long ms = Duration.between(alert.getOpenedAt(), firstExec).toMillis();
                if (ms >= 0) notification.add(ms);
            }
            if (alert.getAcknowledgedAt() != null) {
                mtta.add(Duration.between(alert.getOpenedAt(), alert.getAcknowledgedAt()).toMillis());
            }
            if (alert.getResolvedAt() != null) {
                mttr.add(Duration.between(alert.getOpenedAt(), alert.getResolvedAt()).toMillis());
            }
        }

        long open = windowAlerts.stream().filter(a -> a.getStatus() == Alert.Status.OPEN).count();
        long acknowledged = windowAlerts.stream().filter(a -> a.getStatus() == Alert.Status.ACKNOWLEDGED).count();
        long resolved = windowAlerts.stream().filter(a -> a.getStatus() == Alert.Status.RESOLVED).count();

        long logsCount = from == null
                ? logEvents.count()
                : logEvents.count((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("receivedAt"), from));
        long execCount = from == null ? executions.count() : executions.countByExecutedAtGreaterThanEqual(from);
        long analysisCount = from == null ? analyses.count() : analyses.countByCreatedAtGreaterThanEqual(from);

        return new MetricsResponse(
                windowed ? days : null,
                new Counts(windowAlerts.size(), open, acknowledged, resolved,
                        logsCount, execCount, analysisCount),
                Stat.of(detection), Stat.of(notification), Stat.of(mtta), Stat.of(mttr));
    }
}
