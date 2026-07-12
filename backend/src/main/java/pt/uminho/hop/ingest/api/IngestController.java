package pt.uminho.hop.ingest.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.ingest.IngestService;
import pt.uminho.hop.ingest.domain.LogEvent;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/{serviceId}/logs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(@PathVariable UUID serviceId,
                                      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                      @RequestBody JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "O corpo do pedido tem de ser um objeto JSON");
        }
        LogEvent event = ingestService.ingest(serviceId, apiKey, payload);
        return Map.of(
                "id", event.getId(),
                "receivedAt", event.getReceivedAt().toString()
        );
    }
}
