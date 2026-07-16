package pt.uminho.hop.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.ai.AIAnalyzer;
import pt.uminho.hop.ai.domain.AIAnalysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts/{alertId}/analyses")
public class AIAnalysisController {

    private final AIAnalyzer analyzer;
    private final ObjectMapper mapper;

    public AIAnalysisController(AIAnalyzer analyzer, ObjectMapper mapper) {
        this.analyzer = analyzer;
        this.mapper = mapper;
    }

    public record AnalysisResponse(
            UUID id, UUID alertId, AIAnalysis.Status status,
            String provider, String model, String promptVersion,
            JsonNode inputLogIds, JsonNode output, String error,
            OffsetDateTime createdAt) {}

    @GetMapping
    public List<AnalysisResponse> list(@PathVariable UUID alertId) {
        return analyzer.listForAlert(alertId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public AnalysisResponse run(@PathVariable UUID alertId) {
        return toResponse(analyzer.analyze(alertId));
    }

    private AnalysisResponse toResponse(AIAnalysis a) {
        return new AnalysisResponse(
                a.getId(), a.getAlertId(), a.getStatus(),
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
