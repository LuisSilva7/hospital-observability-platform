package pt.uminho.hop.rules;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pt.uminho.hop.ai.llm.LlmProvider;
import pt.uminho.hop.audit.AuditTrail;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.domain.RuleCondition;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Regras por linguagem natural" (extensão E7): converte um pedido do
 * operador numa sugestão de regra estruturada via LLM. A IA apenas sugere —
 * a sugestão pré-preenche o wizard e é o humano que revê e grava.
 */
@Component
public class RuleSuggester {

    private static final String SYSTEM_PROMPT = """
            És um assistente de uma plataforma de monitorização hospitalar (dados técnicos, sem dados clínicos). \
            Converte o pedido do operador, em linguagem natural, numa regra de monitorização estruturada.
            Tipos de regra:
            - EVENT_MATCH: dispara quando um log cumpre todas as condições; sem janela nem threshold (deixa-os null).
            - COUNT_THRESHOLD: dispara quando pelo menos `threshold` logs cumprem as condições numa janela de `windowMinutes`.
            - NO_ACTIVITY: dispara quando o serviço não envia logs durante `windowMinutes`; sem condições (lista vazia).
            - ANOMALY: deteção estatística — dispara quando a contagem de erros na janela de `windowMinutes` excede o histórico recente por `threshold` desvios-padrão (z-score; 3 se não especificado); sem condições (lista vazia). Usa quando o pedido falar de "anómalo", "fora do normal" ou "acima do habitual" sem números concretos.
            Campos utilizáveis nas condições: `level` (INFO/WARN/ERROR/DEBUG/TRACE/FATAL), `message` e `eventType` \
            são normalizados; qualquer outro campo do payload JSON também pode ser usado, com "." para aninhados (ex.: data.code).
            Operadores: EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN (os dois últimos comparam valores numéricos).
            Regras de resposta:
            - `serviceName` tem de ser EXATAMENTE um dos nomes da lista fornecida; se o pedido não permitir identificar o serviço, devolve string vazia — nunca inventes serviços.
            - Severidade adequada ao impacto (LOW/MEDIUM/HIGH/CRITICAL); erros costumam ser HIGH.
            - `cooldownMinutes` = 10 se o pedido não disser nada.
            - A sugestão será sempre revista por um humano antes de ser gravada.
            - `explanation` curta, em português europeu.""";

    public record SuggestedCondition(
            @JsonPropertyDescription("Campo do log (level, message, eventType ou campo do payload, com '.' para aninhados)")
            String fieldPath,
            RuleCondition.Operator operator,
            @JsonPropertyDescription("Valor esperado, como string")
            String expectedValue) {}

    /** Schema do structured output devolvido pelo LLM. */
    public record RuleSuggestionResult(
            @JsonPropertyDescription("Nome EXATO de um dos serviços listados; string vazia se o pedido não identificar o serviço")
            String serviceName,
            @JsonPropertyDescription("Nome sugerido para a regra, em português (máx. 160 caracteres)")
            String name,
            MonitorRule.Type type,
            MonitorRule.Severity severity,
            @JsonPropertyDescription("Janela em minutos (NO_ACTIVITY e COUNT_THRESHOLD); null para EVENT_MATCH")
            Integer windowMinutes,
            @JsonPropertyDescription("Número mínimo de eventos na janela; só para COUNT_THRESHOLD")
            Integer threshold,
            @JsonPropertyDescription("Minutos de cooldown entre disparos")
            Integer cooldownMinutes,
            @JsonPropertyDescription("Condições sobre os logs; lista vazia para NO_ACTIVITY")
            List<SuggestedCondition> conditions,
            @JsonPropertyDescription("Explicação curta, em português, de como o pedido foi interpretado")
            String explanation) {}

    /** Sugestão devolvida à UI, com o serviço resolvido para id (ou null se ambíguo). */
    public record RuleSuggestionResponse(
            UUID serviceId, String serviceName, String name,
            MonitorRule.Type type, MonitorRule.Severity severity,
            Integer windowMinutes, Integer threshold, Integer cooldownMinutes,
            List<SuggestedCondition> conditions, String explanation) {}

    private final LlmProvider llm;
    private final MonitoredServiceRepository services;
    private final AuditTrail audit;

    public RuleSuggester(LlmProvider llm,
                         MonitoredServiceRepository services,
                         AuditTrail audit) {
        this.llm = llm;
        this.services = services;
        this.audit = audit;
    }

    public RuleSuggestionResponse suggest(String prompt) {
        if (!llm.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Sugestão por IA não configurada: define a variável de ambiente LLM_API_KEY e reinicia o backend.");
        }

        List<MonitoredService> all = services.findAll();
        String serviceList = all.isEmpty()
                ? "(nenhum serviço registado)"
                : all.stream()
                        .map(s -> "- " + s.getName() + " (ambiente " + s.getEnvironment()
                                + ", criticidade " + s.getCriticality() + ")")
                        .collect(Collectors.joining("\n"));

        String userContent = "Pedido do operador: " + prompt.trim()
                + "\n\nServiços disponíveis:\n" + serviceList;

        RuleSuggestionResult result = llm.generate(SYSTEM_PROMPT, userContent, RuleSuggestionResult.class);
        UUID serviceId = resolveService(all, result.serviceName());

        audit.user("RULE_SUGGESTED", "RULE", serviceId, Map.of(
                "prompt", prompt.length() > 200 ? prompt.substring(0, 200) + "…" : prompt,
                "type", result.type() == null ? "?" : result.type().name(),
                "serviceName", result.serviceName() == null ? "" : result.serviceName()));

        return new RuleSuggestionResponse(
                serviceId,
                result.serviceName(),
                result.name(),
                result.type(),
                result.severity(),
                result.windowMinutes(),
                result.threshold(),
                result.cooldownMinutes(),
                result.conditions() == null ? List.of() : result.conditions(),
                result.explanation());
    }

    /** Resolve o nome sugerido para um serviço real: igualdade e depois "contém", sem maiúsculas. */
    static UUID resolveService(List<MonitoredService> all, String suggestedName) {
        if (suggestedName == null || suggestedName.isBlank()) {
            return null;
        }
        String wanted = suggestedName.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(s -> s.getName().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .or(() -> all.stream()
                        .filter(s -> s.getName().toLowerCase(Locale.ROOT).contains(wanted)
                                || wanted.contains(s.getName().toLowerCase(Locale.ROOT)))
                        .findFirst())
                .map(MonitoredService::getId)
                .orElse(null);
    }
}
