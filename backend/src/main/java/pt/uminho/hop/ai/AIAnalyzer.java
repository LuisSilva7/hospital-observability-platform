package pt.uminho.hop.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pt.uminho.hop.ai.domain.AIAnalysis;
import pt.uminho.hop.ai.llm.AnalysisResult;
import pt.uminho.hop.ai.llm.LlmException;
import pt.uminho.hop.ai.llm.LlmProvider;
import pt.uminho.hop.ai.repository.AIAnalysisRepository;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.repository.AlertLogLinkRepository;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.repository.MonitorRuleRepository;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquestra o módulo 8: reúne alerta + regra + serviço + logs associados,
 * constrói o prompt (com redação de campos sensíveis configuráveis),
 * invoca o LlmProvider e persiste o resultado em ai_analysis.
 */
@Component
public class AIAnalyzer {

    static final String PROMPT_VERSION = "v1";
    static final int MAX_LOGS = 20;
    static final int MAX_PAYLOAD_CHARS = 1500;

    private static final Logger log = LoggerFactory.getLogger(AIAnalyzer.class);

    private static final String SYSTEM_PROMPT = """
            És um assistente de observabilidade de uma plataforma de monitorização hospitalar. \
            Os dados são técnicos e operacionais (logs de integração entre serviços) — não há dados clínicos. \
            Analisa o alerta e os logs fornecidos e produz um resumo, a causa provável, evidências e recomendações.
            Regras:
            - Baseia-te APENAS no alerta, na regra e nos logs fornecidos neste pedido; se a informação for insuficiente, di-lo explicitamente.
            - Nas evidências, cita logs concretos de entre os fornecidos (timestamp e mensagem); nunca inventes logs.
            - Não afirmes que alguma ação já foi executada; as recomendações são sugestões para o operador humano.
            - Responde em português europeu.""";

    private final AlertRepository alerts;
    private final MonitorRuleRepository rules;
    private final MonitoredServiceRepository services;
    private final AlertLogLinkRepository logLinks;
    private final LogEventRepository logEvents;
    private final AIAnalysisRepository analyses;
    private final LlmProvider llm;
    private final ObjectMapper mapper;
    private final pt.uminho.hop.audit.AuditTrail audit;
    private final Set<String> redactedFields;

    public AIAnalyzer(AlertRepository alerts,
                      MonitorRuleRepository rules,
                      MonitoredServiceRepository services,
                      AlertLogLinkRepository logLinks,
                      LogEventRepository logEvents,
                      AIAnalysisRepository analyses,
                      LlmProvider llm,
                      ObjectMapper mapper,
                      pt.uminho.hop.audit.AuditTrail audit,
                      @Value("${hop.llm.redacted-fields:}") String redactedFieldsCsv) {
        this.alerts = alerts;
        this.rules = rules;
        this.services = services;
        this.logLinks = logLinks;
        this.logEvents = logEvents;
        this.analyses = analyses;
        this.llm = llm;
        this.mapper = mapper;
        this.audit = audit;
        this.redactedFields = Arrays.stream(redactedFieldsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    public List<AIAnalysis> listForAlert(UUID alertId) {
        requireAlert(alertId);
        return analyses.findByAlertIdOrderByCreatedAtDesc(alertId);
    }

    public AIAnalysis analyze(UUID alertId) {
        Alert alert = requireAlert(alertId);
        if (!llm.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Análise por IA não configurada: define a variável de ambiente LLM_API_KEY e reinicia o backend.");
        }

        List<LogEvent> logs = linkedLogs(alertId);
        String prompt = buildPrompt(alert, logs);

        AIAnalysis analysis = new AIAnalysis();
        analysis.setAlertId(alertId);
        analysis.setProvider(llm.name());
        analysis.setModel(llm.model());
        analysis.setPromptVersion(PROMPT_VERSION);
        analysis.setInputLogIds(writeJson(logs.stream().map(LogEvent::getId).toList()));

        try {
            AnalysisResult result = llm.analyze(SYSTEM_PROMPT, prompt);
            analysis.setStatus(AIAnalysis.Status.SUCCESS);
            analysis.setOutput(writeJson(result));
        } catch (LlmException e) {
            log.warn("Análise de IA falhou para o alerta {}: {}", alertId, e.getMessage());
            analysis.setStatus(AIAnalysis.Status.FAILED);
            analysis.setError(e.getMessage());
        }
        AIAnalysis saved = analyses.save(analysis);
        audit.user("AI_ANALYSIS_REQUESTED", "AI_ANALYSIS", saved.getId(), java.util.Map.of(
                "alertId", alertId.toString(),
                "status", saved.getStatus().name(),
                "model", llm.model()));
        return saved;
    }

    private Alert requireAlert(UUID alertId) {
        return alerts.findById(alertId)
                .orElseThrow(() -> new NotFoundException("Alerta não encontrado"));
    }

    private List<LogEvent> linkedLogs(UUID alertId) {
        List<UUID> ids = logLinks.findByAlertId(alertId).stream()
                .map(l -> l.getLogEventId())
                .toList();
        return logEvents.findAllById(ids).stream()
                .sorted(Comparator.comparing(LogEvent::getReceivedAt).reversed())
                .limit(MAX_LOGS)
                .toList();
    }

    private String buildPrompt(Alert alert, List<LogEvent> logs) {
        String serviceName = services.findById(alert.getServiceId())
                .map(MonitoredService::getName).orElse("(removido)");
        String serviceEnv = services.findById(alert.getServiceId())
                .map(s -> s.getEnvironment() + " / criticidade " + s.getCriticality()).orElse("desconhecido");
        String ruleDescription = alert.getRuleId() == null ? "(sem regra associada)"
                : rules.findById(alert.getRuleId())
                        .map(r -> r.getName() + " (tipo " + r.getType() + ")")
                        .orElse("(regra removida)");

        StringBuilder sb = new StringBuilder();
        sb.append("## Alerta\n")
                .append("Título: ").append(alert.getTitle()).append('\n')
                .append("Severidade: ").append(alert.getSeverity()).append('\n')
                .append("Estado: ").append(alert.getStatus()).append('\n')
                .append("Aberto em: ").append(alert.getOpenedAt()).append('\n')
                .append("Serviço: ").append(serviceName).append(" (").append(serviceEnv).append(")\n")
                .append("Regra que disparou: ").append(ruleDescription).append('\n');

        sb.append("\n## Logs associados (").append(logs.size()).append(", mais recentes primeiro)\n");
        if (logs.isEmpty()) {
            sb.append("(sem logs associados — provavelmente uma regra de ausência de atividade)\n");
        }
        for (LogEvent event : logs) {
            sb.append("- [").append(event.getReceivedAt()).append("] ")
                    .append(event.getLevel() == null ? "?" : event.getLevel()).append(' ')
                    .append(event.getMessage() == null ? "(sem mensagem)" : event.getMessage()).append('\n')
                    .append("  payload: ").append(sanitizePayload(event.getPayload())).append('\n');
        }
        return sb.toString();
    }

    /** Remove campos sensíveis configurados e trunca o payload para limitar o contexto. */
    String sanitizePayload(String payload) {
        if (payload == null) return "{}";
        String result = payload;
        if (!redactedFields.isEmpty()) {
            try {
                JsonNode node = mapper.readTree(payload);
                redact(node);
                result = mapper.writeValueAsString(node);
            } catch (JsonProcessingException e) {
                // payload não é JSON válido — segue truncado tal como está
            }
        }
        return result.length() <= MAX_PAYLOAD_CHARS
                ? result
                : result.substring(0, MAX_PAYLOAD_CHARS) + "…(truncado)";
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> names = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (redactedFields.contains(name.toLowerCase(Locale.ROOT))) {
                    obj.put(name, "[removido]");
                } else {
                    redact(obj.get(name));
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(this::redact);
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha a serializar JSON", e);
        }
    }
}
