package pt.uminho.hop.alerts.api;

import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.alerts.AlertManager;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.domain.AlertEvent;
import pt.uminho.hop.alerts.repository.AlertEventRepository;
import pt.uminho.hop.alerts.repository.AlertLogLinkRepository;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alerts;
    private final AlertEventRepository events;
    private final AlertLogLinkRepository logLinks;
    private final LogEventRepository logEvents;
    private final MonitoredServiceRepository services;
    private final AlertManager manager;
    private final pt.uminho.hop.automations.repository.ActionExecutionRepository actionExecutions;

    public AlertController(AlertRepository alerts,
                           AlertEventRepository events,
                           AlertLogLinkRepository logLinks,
                           LogEventRepository logEvents,
                           MonitoredServiceRepository services,
                           AlertManager manager,
                           pt.uminho.hop.automations.repository.ActionExecutionRepository actionExecutions) {
        this.alerts = alerts;
        this.events = events;
        this.logLinks = logLinks;
        this.logEvents = logEvents;
        this.services = services;
        this.manager = manager;
        this.actionExecutions = actionExecutions;
    }

    public record AlertResponse(
            UUID id, UUID serviceId, String serviceName, UUID ruleId,
            String title, MonitorRule.Severity severity, Alert.Status status,
            OffsetDateTime openedAt, OffsetDateTime acknowledgedAt, OffsetDateTime resolvedAt) {}

    public record TimelineEvent(UUID id, AlertEvent.Type type, String description, OffsetDateTime createdAt) {}

    public record LinkedLog(UUID id, OffsetDateTime receivedAt, String level, String message, String payload) {}

    public record ExecutionSummary(
            UUID id, String status, String actionType, int attempts, Integer responseCode,
            String error, OffsetDateTime executedAt) {}

    public record AlertDetailResponse(
            AlertResponse alert, List<TimelineEvent> timeline, List<LinkedLog> logs,
            List<ExecutionSummary> executions) {}

    @GetMapping
    public List<AlertResponse> list(@RequestParam(required = false) Alert.Status status) {
        List<Alert> result = status == null
                ? alerts.findAllByOrderByOpenedAtDesc()
                : alerts.findByStatusInOrderByOpenedAtDesc(List.of(status));
        Map<UUID, String> names = serviceNames();
        return result.stream().map(a -> toResponse(a, names)).toList();
    }

    @GetMapping("/{id}")
    public AlertDetailResponse get(@PathVariable UUID id) {
        Alert alert = alerts.findById(id)
                .orElseThrow(() -> new NotFoundException("Alerta não encontrado"));

        List<TimelineEvent> timeline = events.findByAlertIdOrderByCreatedAtAsc(id).stream()
                .map(e -> new TimelineEvent(e.getId(), e.getType(), e.getDescription(), e.getCreatedAt()))
                .toList();

        List<UUID> logIds = logLinks.findByAlertId(id).stream()
                .map(l -> l.getLogEventId()).toList();
        List<LinkedLog> logs = logEvents.findAllById(logIds).stream()
                .sorted((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()))
                .map(this::toLinkedLog)
                .toList();

        // o tipo é lido da própria execução (persistido no momento em que correu),
        // por isso o rótulo mantém-se correto mesmo que a automação seja depois editada
        List<ExecutionSummary> execs = actionExecutions.findByAlertIdOrderByExecutedAtDesc(id).stream()
                .map(e -> new ExecutionSummary(e.getId(), e.getStatus().name(), e.getActionType().name(),
                        e.getAttempts(), e.getResponseCode(), e.getError(), e.getExecutedAt()))
                .toList();

        return new AlertDetailResponse(toResponse(alert, serviceNames()), timeline, logs, execs);
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID id) {
        return toResponse(manager.acknowledge(id), serviceNames());
    }

    @PostMapping("/{id}/resolve")
    public AlertResponse resolve(@PathVariable UUID id) {
        return toResponse(manager.resolve(id), serviceNames());
    }

    private Map<UUID, String> serviceNames() {
        return services.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
    }

    private AlertResponse toResponse(Alert a, Map<UUID, String> names) {
        return new AlertResponse(
                a.getId(), a.getServiceId(),
                names.getOrDefault(a.getServiceId(), "(removido)"),
                a.getRuleId(), a.getTitle(), a.getSeverity(), a.getStatus(),
                a.getOpenedAt(), a.getAcknowledgedAt(), a.getResolvedAt());
    }

    private LinkedLog toLinkedLog(LogEvent e) {
        return new LinkedLog(e.getId(), e.getReceivedAt(), e.getLevel(), e.getMessage(), e.getPayload());
    }
}
