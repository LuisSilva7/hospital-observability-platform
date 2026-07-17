package pt.uminho.hop.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.ai.AIAnalyzer;
import pt.uminho.hop.ai.domain.AIAnalysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Insights de IA ao nível do serviço (extensão E8) — não dependem de um alerta. */
@RestController
@RequestMapping("/api/services/{serviceId}/analyses")
public class ServiceInsightController {

    private final AIAnalyzer analyzer;
    private final ObjectMapper mapper;

    public ServiceInsightController(AIAnalyzer analyzer, ObjectMapper mapper) {
        this.analyzer = analyzer;
        this.mapper = mapper;
    }

    public record InsightResponse(
            UUID id, UUID serviceId, AIAnalysis.Status status,
            String provider, String model, String promptVersion,
            JsonNode inputLogIds, JsonNode output, String error,
            OffsetDateTime createdAt) {}

    @GetMapping
    public List<InsightResponse> list(@PathVariable UUID serviceId) {
        return analyzer.listForService(serviceId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public InsightResponse run(@PathVariable UUID serviceId) {
        return toResponse(analyzer.analyzeService(serviceId));
    }

    private InsightResponse toResponse(AIAnalysis a) {
        return new InsightResponse(
                a.getId(), a.getServiceId(), a.getStatus(),
                a.getProvider(), a.getModel(), a.getPromptVersion(),
                readJson(a.getInputLogIds()), readJson(a.getOutput()), a.getError(),
                a.getCreatedAt());
    }

    private JsonNode readJson(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
