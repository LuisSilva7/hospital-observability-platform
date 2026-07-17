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
    private final pt.uminho.hop.ai.AIAnalyzer aiAnalyzer;
    private final pt.uminho.hop.events.SseHub sse;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender;
    private final String mailHost;
    private final String mailFrom;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    public AutomationExecutor(AutomationRepository automations,
                              ActionExecutionRepository executions,
                              AlertRepository alerts,
                              MonitoredServiceRepository services,
                              ObjectMapper mapper,
                              pt.uminho.hop.audit.AuditTrail audit,
                              pt.uminho.hop.ai.AIAnalyzer aiAnalyzer,
                              pt.uminho.hop.events.SseHub sse,
                              org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender,
                              @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String mailHost,
                              @org.springframework.beans.factory.annotation.Value("${hop.mail.from:hop@localhost}") String mailFrom) {
        this.automations = automations;
        this.executions = executions;
        this.alerts = alerts;
        this.services = services;
        this.mapper = mapper;
        this.audit = audit;
        this.aiAnalyzer = aiAnalyzer;
        this.sse = sse;
        this.mailSender = mailSender;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.mailFrom = mailFrom;
    }

    public boolean isMailConfigured() {
        return !mailHost.isBlank() && mailSender.getIfAvailable() != null;
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
        ActionExecution execution = switch (action.getType()) {
            case AI_ANALYSIS -> executeAiAnalysis(action, alert);
            case EMAIL -> executeEmail(action, alert);
            case TEAMS -> executeTeams(action, alert);
            case WEBHOOK -> doExecute(action, alert);
        };
        audit.system("ACTION_EXECUTED", "AUTOMATION",
                action.getAutomation() == null ? null : action.getAutomation().getId(),
                java.util.Map.of(
                        "type", action.getType().name(),
                        "status", execution.getStatus().name(),
                        "attempts", execution.getAttempts(),
                        "alertTitle", alert.getTitle()));
        sse.publish("executions");
        return execution;
    }

    /** Ação AI_ANALYSIS: pede a análise ao AIAnalyzer em vez de chamar um webhook. */
    private ActionExecution executeAiAnalysis(AutomationAction action, Alert alert) {
        ActionExecution execution = new ActionExecution();
        execution.setActionId(action.getId());
        execution.setActionType(action.getType());
        execution.setAlertId(alert.getId());
        execution.setAttempts(1);

        if (alert.getId() == null) {
            // alerta fictício do endpoint /test — não há logs para analisar
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError("O teste de análise IA precisa de um alerta real — dispara a regra primeiro.");
            return executions.save(execution);
        }
        try {
            var analysis = aiAnalyzer.analyzeAutomatically(alert.getId());
            if (analysis.getStatus() == pt.uminho.hop.ai.domain.AIAnalysis.Status.SUCCESS) {
                execution.setStatus(ActionExecution.Status.SUCCESS);
                execution.setResponseBody("Análise de IA criada: " + analysis.getId());
            } else {
                execution.setStatus(ActionExecution.Status.FAILED);
                execution.setError(analysis.getError());
            }
        } catch (Exception e) {
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError(e.getMessage());
            log.warn("Análise IA automática falhou para o alerta {}: {}", alert.getId(), e.getMessage());
        }
        return executions.save(execution);
    }

    /** Ação EMAIL: envia por SMTP (spring-mail); sem SMTP configurado falha com mensagem clara. */
    private ActionExecution executeEmail(AutomationAction action, Alert alert) {
        ActionExecution execution = new ActionExecution();
        execution.setActionId(action.getId());
        execution.setActionType(action.getType());
        execution.setAlertId(alert.getId());
        execution.setAttempts(1);

        try {
            if (!isMailConfigured()) {
                execution.setStatus(ActionExecution.Status.FAILED);
                execution.setError("SMTP não configurado: define SMTP_HOST (e SMTP_USERNAME/SMTP_PASSWORD/SMTP_FROM) e reinicia o backend.");
                return executions.save(execution);
            }
            JsonNode config = mapper.readTree(action.getConfig());
            Map<String, String> values = alertValues(alert);

            var message = new org.springframework.mail.SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(config.path("to").asText().split("\\s*,\\s*"));
            message.setSubject(render(
                    config.path("subjectTemplate").asText("[HOP] {{severity}} · {{title}}"), values));
            message.setText("""
                    Alerta na plataforma de observabilidade hospitalar

                    Título: %s
                    Serviço: %s
                    Severidade: %s
                    Estado: %s
                    Aberto em: %s

                    Detalhe: %s/alerts/%s
                    """.formatted(values.get("title"), values.get("serviceName"),
                    values.get("severity"), values.get("status"), values.get("openedAt"),
                    "http://localhost:3000", values.get("alertId")));

            mailSender.getObject().send(message);
            execution.setStatus(ActionExecution.Status.SUCCESS);
            execution.setResponseBody("Email enviado para " + config.path("to").asText());
        } catch (Exception e) {
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError(e.getMessage() != null ? e.getMessage() : e.toString());
            log.warn("Email falhou para o alerta {}: {}", alert.getId(), execution.getError());
        }
        return executions.save(execution);
    }

    /** Ação TEAMS: incoming webhook com MessageCard; reutiliza o envio HTTP com retries. */
    private ActionExecution executeTeams(AutomationAction action, Alert alert) {
        ActionExecution execution = new ActionExecution();
        execution.setActionId(action.getId());
        execution.setActionType(action.getType());
        execution.setAlertId(alert.getId());

        try {
            JsonNode config = mapper.readTree(action.getConfig());
            Map<String, String> values = alertValues(alert);

            ObjectNode card = mapper.createObjectNode();
            card.put("@type", "MessageCard");
            card.put("@context", "http://schema.org/extensions");
            card.put("themeColor", switch (alert.getSeverity()) {
                case CRITICAL, HIGH -> "D64545";
                case MEDIUM -> "D97706";
                case LOW -> "3B82F6";
            });
            card.put("summary", values.get("title"));
            ObjectNode section = card.putArray("sections").addObject();
            section.put("activityTitle", "🚨 " + values.get("title"));
            section.put("activitySubtitle", "Plataforma de observabilidade hospitalar");
            var facts = section.putArray("facts");
            facts.addObject().put("name", "Serviço").put("value", values.get("serviceName"));
            facts.addObject().put("name", "Severidade").put("value", values.get("severity"));
            facts.addObject().put("name", "Estado").put("value", values.get("status"));
            facts.addObject().put("name", "Aberto em").put("value", values.get("openedAt"));

            return sendWithRetries(execution, config.path("url").asText(), "POST",
                    card.toString(), mapper.createObjectNode());
        } catch (Exception e) {
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError("Configuração inválida: " + e.getMessage());
            return executions.save(execution);
        }
    }

    private ActionExecution doExecute(AutomationAction action, Alert alert) {
        ActionExecution execution = new ActionExecution();
        execution.setActionId(action.getId());
        execution.setActionType(action.getType());
        execution.setAlertId(alert.getId());

        try {
            JsonNode config = mapper.readTree(action.getConfig());
            return sendWithRetries(execution, config.path("url").asText(),
                    config.path("method").asText("POST"), buildPayload(config, alert),
                    config.path("headers"));
        } catch (Exception e) {
            execution.setStatus(ActionExecution.Status.FAILED);
            execution.setError("Configuração inválida: " + e.getMessage());
            return executions.save(execution);
        }
    }

    /** Envio HTTP com até MAX_ATTEMPTS tentativas e backoff; persiste o resultado. */
    private ActionExecution sendWithRetries(ActionExecution execution, String url,
                                            String method, String body, JsonNode headers) {
        try {
            Exception lastError = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                execution.setAttempts(attempt);
                try {
                    HttpRequest.Builder request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(HTTP_TIMEOUT)
                            .method(method, HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json");
                    headers.properties().forEach(e ->
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
        log.warn("Ação {} falhou (alerta {}): {}", execution.getActionId(), execution.getAlertId(),
                execution.getError());
        return executions.save(execution);
    }

    /** Valores do alerta usados em templates e cartões. */
    private Map<String, String> alertValues(Alert alert) {
        String serviceName = services.findById(alert.getServiceId())
                .map(s -> s.getName()).orElse("(removido)");
        return Map.of(
                "alertId", String.valueOf(alert.getId()),
                "title", alert.getTitle(),
                "severity", alert.getSeverity().name(),
                "status", alert.getStatus().name(),
                "serviceName", serviceName,
                "openedAt", String.valueOf(alert.getOpenedAt()));
    }

    private static String render(String template, Map<String, String> values) {
        String result = template;
        for (var entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /** Template com placeholders {{...}} ou, por omissão, JSON com os dados do alerta. */
    private String buildPayload(JsonNode config, Alert alert) {
        Map<String, String> values = alertValues(alert);
        String template = config.path("payloadTemplate").asText("");
        if (!template.isBlank()) {
            return render(template, values);
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
