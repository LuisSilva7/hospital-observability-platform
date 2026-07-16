package pt.uminho.hop.automations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.automations.domain.ActionExecution;
import pt.uminho.hop.automations.domain.Automation;
import pt.uminho.hop.automations.domain.AutomationAction;
import pt.uminho.hop.automations.repository.ActionExecutionRepository;
import pt.uminho.hop.automations.repository.AutomationRepository;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Executa as ações (webhooks) associadas à regra quando um alerta é criado.
 * Corre em thread própria (não bloqueia a ingestão); cada ação tem até
 * MAX_ATTEMPTS tentativas com backoff; o resultado fica em action_execution.
 */
@Component
public class AutomationExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutomationExecutor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_STORED = 2000;

    private final AutomationRepository automations;
    private final ActionExecutionRepository executions;
    private final AlertRepository alerts;
    private final MonitoredServiceRepository services;
    private final ObjectMapper mapper;
    private final pt.uminho.hop.audit.AuditTrail audit;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    public AutomationExecutor(AutomationRepository automations,
                              ActionExecutionRepository executions,
                              AlertRepository alerts,
                              MonitoredServiceRepository services,
                              ObjectMapper mapper,
                              pt.uminho.hop.audit.AuditTrail audit) {
        this.automations = automations;
        this.executions = executions;
        this.alerts = alerts;
        this.services = services;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Async
    @TransactionalEventListener
    public void onAlertCreated(AlertCreatedEvent event) {
        Alert alert = alerts.findById(event.alertId()).orElse(null);
        if (alert == null) return;
        for (Automation automation : automations.findByRuleIdAndEnabledTrue(event.ruleId())) {
            for (AutomationAction action : automation.getActions()) {
                execute(action, alert);
            }
        }
    }

    /** Executa uma ação para um alerta (real ou de teste) e persiste o resultado. */
    public ActionExecution execute(AutomationAction action, Alert alert) {
        ActionExecution execution = doExecute(action, alert);
        audit.system("ACTION_EXECUTED", "AUTOMATION",
                action.getAutomation() == null ? null : action.getAutomation().getId(),
                java.util.Map.of(
                        "status", execution.getStatus().name(),
                        "attempts", execution.getAttempts(),
                        "alertTitle", alert.getTitle()));
        return execution;
    }

    private ActionExecution doExecute(AutomationAction action, Alert alert) {
        ActionExecution execution = new ActionExecution();
        execution.setActionId(action.getId());
        execution.setAlertId(alert.getId());

        try {
            JsonNode config = mapper.readTree(action.getConfig());
            String url = config.path("url").asText();
            String method = config.path("method").asText("POST");
            String body = buildPayload(config, alert);

            Exception lastError = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                execution.setAttempts(attempt);
                try {
                    HttpRequest.Builder request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(HTTP_TIMEOUT)
                            .method(method, HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json");
                    config.path("headers").properties().forEach(e ->
                            request.header(e.getKey(), e.getValue().asText()));

                    HttpResponse<String> response =
                            http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                    execution.setResponseCode(response.statusCode());
                    execution.setResponseBody(truncate(response.body()));

                    if (response.statusCode() < 400) {
                        execution.setStatus(ActionExecution.Status.SUCCESS);
                        return executions.save(execution);
                    }
                    lastError = new IllegalStateException("HTTP " + response.statusCode());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastError = e;
                    break;
                } catch (Exception e) {
                    lastError = e;
                }
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_BACKOFF.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError(lastError == null ? "desconhecido"
                    : (lastError.getMessage() != null ? lastError.getMessage() : lastError.toString()));
        } catch (Exception e) {
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError("Configuração inválida: " + e.getMessage());
        }
        log.warn("Ação {} falhou para o alerta {}: {}", action.getId(), alert.getId(), execution.getError());
        return executions.save(execution);
    }

    /** Template com placeholders {{...}} ou, por omissão, JSON com os dados do alerta. */
    private String buildPayload(JsonNode config, Alert alert) {
        String serviceName = services.findById(alert.getServiceId())
                .map(s -> s.getName()).orElse("(removido)");
        Map<String, String> values = Map.of(
                "alertId", String.valueOf(alert.getId()),
                "title", alert.getTitle(),
                "severity", alert.getSeverity().name(),
                "status", alert.getStatus().name(),
                "serviceName", serviceName,
                "openedAt", String.valueOf(alert.getOpenedAt()));

        String template = config.path("payloadTemplate").asText("");
        if (!template.isBlank()) {
            String result = template;
            for (var entry : values.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        }
        ObjectNode json = mapper.createObjectNode();
        values.forEach(json::put);
        return json.toString();
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_BODY_STORED ? value : value.substring(0, MAX_BODY_STORED);
    }
}
