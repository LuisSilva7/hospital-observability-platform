package pt.uminho.hop.rules.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.domain.RuleCondition;
import pt.uminho.hop.rules.domain.RuleEvaluation;
import pt.uminho.hop.rules.repository.MonitorRuleRepository;
import pt.uminho.hop.rules.repository.RuleEvaluationRepository;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final MonitorRuleRepository rules;
    private final RuleEvaluationRepository evaluations;
    private final MonitoredServiceRepository services;
    private final pt.uminho.hop.audit.AuditTrail audit;
    private final pt.uminho.hop.events.SseHub sse;
    private final pt.uminho.hop.rules.RuleSuggester suggester;

    public RuleController(MonitorRuleRepository rules,
                          RuleEvaluationRepository evaluations,
                          MonitoredServiceRepository services,
                          pt.uminho.hop.audit.AuditTrail audit,
                          pt.uminho.hop.events.SseHub sse,
                          pt.uminho.hop.rules.RuleSuggester suggester) {
        this.rules = rules;
        this.evaluations = evaluations;
        this.services = services;
        this.audit = audit;
        this.sse = sse;
        this.suggester = suggester;
    }

    // --- DTOs ---

    public record ConditionDto(
            @NotBlank @Size(max = 200) String fieldPath,
            @NotNull RuleCondition.Operator operator,
            @NotBlank @Size(max = 500) String expectedValue) {}

    public record RuleRequest(
            @NotNull UUID serviceId,
            @NotBlank @Size(max = 160) String name,
            @NotNull MonitorRule.Type type,
            @NotNull MonitorRule.Severity severity,
            @Min(1) Integer windowMinutes,
            @Min(1) Integer threshold,
            @Min(0) Integer cooldownMinutes,
            List<ConditionDto> conditions) {}

    public record RuleResponse(
            UUID id, UUID serviceId, String serviceName, String name,
            MonitorRule.Type type, MonitorRule.Severity severity, boolean enabled,
            Integer windowMinutes, Integer threshold, int cooldownMinutes,
            OffsetDateTime lastTriggeredAt, OffsetDateTime createdAt,
            List<ConditionDto> conditions) {}

    public record EvaluationResponse(UUID id, OffsetDateTime triggeredAt, UUID logEventId, String details) {}

    // --- endpoints ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RuleResponse create(@Valid @RequestBody RuleRequest request) {
        validate(request);
        MonitorRule rule = new MonitorRule();
        apply(rule, request);
        RuleResponse response = toResponse(rules.save(rule));
        audit.user("RULE_CREATED", "RULE", response.id(),
                Map.of("name", response.name(), "type", response.type().name()));
        sse.publish("rules");
        return response;
    }

    @GetMapping
    public List<RuleResponse> list(@RequestParam(required = false) UUID serviceId) {
        return rules.findAll().stream()
                .filter(r -> serviceId == null || r.getServiceId().equals(serviceId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public RuleResponse get(@PathVariable UUID id) {
        return toResponse(find(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        validate(request);
        MonitorRule rule = find(id);
        apply(rule, request);
        RuleResponse response = toResponse(rules.save(rule));
        audit.user("RULE_UPDATED", "RULE", id,
                Map.of("name", response.name(), "type", response.type().name()));
        sse.publish("rules");
        return response;
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public RuleResponse setEnabled(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        MonitorRule rule = find(id);
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        rule.setEnabled(enabled);
        RuleResponse response = toResponse(rules.save(rule));
        audit.user(enabled ? "RULE_ENABLED" : "RULE_DISABLED", "RULE", id,
                Map.of("name", response.name()));
        sse.publish("rules");
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        MonitorRule rule = find(id);
        String name = rule.getName();
        rules.delete(rule);
        audit.user("RULE_DELETED", "RULE", id, Map.of("name", name));
        sse.publish("rules");
    }

    public record SuggestRequest(@NotBlank @Size(max = 1000) String prompt) {}

    /** Sugestão de regra por linguagem natural (IA). Não grava nada — só pré-preenche o wizard. */
    @PostMapping("/suggest")
    public pt.uminho.hop.rules.RuleSuggester.RuleSuggestionResponse suggest(
            @Valid @RequestBody SuggestRequest request) {
        return suggester.suggest(request.prompt());
    }

    @GetMapping("/{id}/evaluations")
    public List<EvaluationResponse> evaluations(@PathVariable UUID id) {
        find(id);
        return evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(id).stream()
                .map(e -> new EvaluationResponse(e.getId(), e.getTriggeredAt(), e.getLogEventId(), e.getDetails()))
                .toList();
    }

    // --- helpers ---

    private void validate(RuleRequest request) {
        if (!services.existsById(request.serviceId())) {
            throw new NotFoundException("Serviço não encontrado");
        }
        boolean needsConditions = request.type() != MonitorRule.Type.NO_ACTIVITY
                && request.type() != MonitorRule.Type.ANOMALY;
        boolean hasConditions = request.conditions() != null && !request.conditions().isEmpty();
        if (needsConditions && !hasConditions) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Regras " + request.type() + " precisam de pelo menos uma condição");
        }
        if ((request.type() == MonitorRule.Type.NO_ACTIVITY || request.type() == MonitorRule.Type.ANOMALY)
                && request.windowMinutes() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Regras " + request.type() + " precisam de windowMinutes");
        }
        if (request.type() == MonitorRule.Type.COUNT_THRESHOLD
                && (request.windowMinutes() == null || request.threshold() == null)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Regras COUNT_THRESHOLD precisam de windowMinutes e threshold");
        }
    }

    private void apply(MonitorRule rule, RuleRequest request) {
        rule.setServiceId(request.serviceId());
        rule.setName(request.name().trim());
        rule.setType(request.type());
        rule.setSeverity(request.severity());
        rule.setWindowMinutes(request.windowMinutes());
        rule.setThreshold(request.threshold());
        rule.setCooldownMinutes(request.cooldownMinutes() == null ? 10 : request.cooldownMinutes());
        rule.getConditions().clear();
        if (request.conditions() != null) {
            for (ConditionDto dto : request.conditions()) {
                RuleCondition condition = new RuleCondition();
                condition.setRule(rule);
                condition.setFieldPath(dto.fieldPath().trim());
                condition.setOperator(dto.operator());
                condition.setExpectedValue(dto.expectedValue());
                rule.getConditions().add(condition);
            }
        }
    }

    private MonitorRule find(UUID id) {
        return rules.findById(id).orElseThrow(() -> new NotFoundException("Regra não encontrada"));
    }

    private RuleResponse toResponse(MonitorRule rule) {
        String serviceName = services.findById(rule.getServiceId())
                .map(s -> s.getName()).orElse("(removido)");
        return new RuleResponse(
                rule.getId(), rule.getServiceId(), serviceName, rule.getName(),
                rule.getType(), rule.getSeverity(), rule.isEnabled(),
                rule.getWindowMinutes(), rule.getThreshold(), rule.getCooldownMinutes(),
                rule.getLastTriggeredAt(), rule.getCreatedAt(),
                rule.getConditions().stream()
                        .map(c -> new ConditionDto(c.getFieldPath(), c.getOperator(), c.getExpectedValue()))
                        .collect(Collectors.toList()));
    }
}
