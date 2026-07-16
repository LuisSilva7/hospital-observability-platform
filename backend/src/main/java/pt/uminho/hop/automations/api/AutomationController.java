package pt.uminho.hop.automations.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.automations.AutomationExecutor;
import pt.uminho.hop.automations.domain.ActionExecution;
import pt.uminho.hop.automations.domain.Automation;
import pt.uminho.hop.automations.domain.AutomationAction;
import pt.uminho.hop.automations.repository.ActionExecutionRepository;
import pt.uminho.hop.automations.repository.AutomationRepository;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.repository.MonitorRuleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/automations")
public class AutomationController {

    private final AutomationRepository automations;
    private final MonitorRuleRepository rules;
    private final ActionExecutionRepository executions;
    private final AutomationExecutor executor;
    private final ObjectMapper mapper;
    private final pt.uminho.hop.audit.AuditTrail audit;

    public AutomationController(AutomationRepository automations,
                                MonitorRuleRepository rules,
                                ActionExecutionRepository executions,
                                AutomationExecutor executor,
                                ObjectMapper mapper,
                                pt.uminho.hop.audit.AuditTrail audit) {
        this.automations = automations;
        this.rules = rules;
        this.executions = executions;
        this.executor = executor;
        this.mapper = mapper;
        this.audit = audit;
    }

    public record WebhookConfig(
            @NotBlank String url,
            String method,
            Map<String, String> headers,
            String payloadTemplate) {}

    public record AutomationRequest(
            @NotNull UUID ruleId,
            @NotBlank @Size(max = 160) String name,
            @NotNull @Valid WebhookConfig webhook) {}

    public record AutomationResponse(
            UUID id, UUID ruleId, String ruleName, String name, boolean enabled,
            OffsetDateTime createdAt, WebhookConfig webhook, UUID actionId) {}

    public record ExecutionResponse(
            UUID id, UUID actionId, UUID alertId, ActionExecution.Status status,
            int attempts, Integer responseCode, String responseBody, String error,
            OffsetDateTime executedAt) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AutomationResponse create(@Valid @RequestBody AutomationRequest request) {
        if (!rules.existsById(request.ruleId())) {
            throw new NotFoundException("Regra não encontrada");
        }
        Automation automation = new Automation();
        apply(automation, request);
        AutomationResponse response = toResponse(automations.save(automation));
        audit.user("AUTOMATION_CREATED", "AUTOMATION", response.id(), Map.of("name", response.name()));
        return response;
    }

    @GetMapping
    public List<AutomationResponse> list() {
        return automations.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public AutomationResponse get(@PathVariable UUID id) {
        return toResponse(find(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public AutomationResponse update(@PathVariable UUID id, @Valid @RequestBody AutomationRequest request) {
        if (!rules.existsById(request.ruleId())) {
            throw new NotFoundException("Regra não encontrada");
        }
        Automation automation = find(id);
        apply(automation, request);
        AutomationResponse response = toResponse(automations.save(automation));
        audit.user("AUTOMATION_UPDATED", "AUTOMATION", id, Map.of("name", response.name()));
        return response;
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public AutomationResponse setEnabled(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        Automation automation = find(id);
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        automation.setEnabled(enabled);
        AutomationResponse response = toResponse(automations.save(automation));
        audit.user(enabled ? "AUTOMATION_ENABLED" : "AUTOMATION_DISABLED", "AUTOMATION", id,
                Map.of("name", response.name()));
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        Automation automation = find(id);
        String name = automation.getName();
        automations.delete(automation);
        audit.user("AUTOMATION_DELETED", "AUTOMATION", id, Map.of("name", name));
    }

    /** Executa o webhook com um alerta fictício, de forma síncrona, e devolve o resultado. */
    @PostMapping("/{id}/test")
    public ExecutionResponse test(@PathVariable UUID id) {
        Automation automation = find(id);
        MonitorRule rule = rules.findById(automation.getRuleId()).orElseThrow();

        Alert fake = new Alert();
        fake.setServiceId(rule.getServiceId());
        fake.setRuleId(rule.getId());
        fake.setTitle("[TESTE] " + automation.getName());
        fake.setSeverity(rule.getSeverity());

        ActionExecution result = executor.execute(automation.getActions().get(0), fake);
        audit.user("AUTOMATION_TESTED", "AUTOMATION", id,
                Map.of("name", automation.getName(), "status", result.getStatus().name()));
        return toExecutionResponse(result);
    }

    @GetMapping("/{id}/executions")
    public List<ExecutionResponse> executions(@PathVariable UUID id) {
        Automation automation = find(id);
        List<UUID> actionIds = automation.getActions().stream().map(AutomationAction::getId).toList();
        return executions.findTop50ByActionIdInOrderByExecutedAtDesc(actionIds).stream()
                .map(this::toExecutionResponse)
                .toList();
    }

    private void apply(Automation automation, AutomationRequest request) {
        automation.setRuleId(request.ruleId());
        automation.setName(request.name().trim());
        automation.getActions().clear();

        ObjectNode config = mapper.createObjectNode();
        config.put("url", request.webhook().url().trim());
        config.put("method", request.webhook().method() == null ? "POST" : request.webhook().method());
        if (request.webhook().headers() != null && !request.webhook().headers().isEmpty()) {
            ObjectNode headers = config.putObject("headers");
            request.webhook().headers().forEach(headers::put);
        }
        if (request.webhook().payloadTemplate() != null && !request.webhook().payloadTemplate().isBlank()) {
            config.put("payloadTemplate", request.webhook().payloadTemplate());
        }

        AutomationAction action = new AutomationAction();
        action.setAutomation(automation);
        action.setType(AutomationAction.Type.WEBHOOK);
        action.setConfig(config.toString());
        automation.getActions().add(action);
    }

    private Automation find(UUID id) {
        return automations.findById(id)
                .orElseThrow(() -> new NotFoundException("Automação não encontrada"));
    }

    private AutomationResponse toResponse(Automation a) {
        String ruleName = rules.findById(a.getRuleId()).map(MonitorRule::getName).orElse("(removida)");
        WebhookConfig webhook = null;
        UUID actionId = null;
        if (!a.getActions().isEmpty()) {
            AutomationAction action = a.getActions().get(0);
            actionId = action.getId();
            try {
                var config = mapper.readTree(action.getConfig());
                Map<String, String> headers = new java.util.LinkedHashMap<>();
                config.path("headers").properties().forEach(e -> headers.put(e.getKey(), e.getValue().asText()));
                webhook = new WebhookConfig(
                        config.path("url").asText(),
                        config.path("method").asText("POST"),
                        headers,
                        config.path("payloadTemplate").asText(null));
            } catch (Exception ignored) {
            }
        }
        return new AutomationResponse(a.getId(), a.getRuleId(), ruleName, a.getName(),
                a.isEnabled(), a.getCreatedAt(), webhook, actionId);
    }

    private ExecutionResponse toExecutionResponse(ActionExecution e) {
        return new ExecutionResponse(e.getId(), e.getActionId(), e.getAlertId(), e.getStatus(),
                e.getAttempts(), e.getResponseCode(), e.getResponseBody(), e.getError(), e.getExecutedAt());
    }
}
