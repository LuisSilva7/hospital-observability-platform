package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.common.UnauthorizedException;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.services.ApiKeyGenerator;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.domain.ServiceApiKey;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;
import pt.uminho.hop.services.repository.ServiceApiKeyRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class IngestService {

    private final MonitoredServiceRepository services;
    private final ServiceApiKeyRepository apiKeys;
    private final LogEventRepository logEvents;

    public IngestService(MonitoredServiceRepository services,
                         ServiceApiKeyRepository apiKeys,
                         LogEventRepository logEvents) {
        this.services = services;
        this.apiKeys = apiKeys;
        this.logEvents = logEvents;
    }

    @Transactional
    public LogEvent ingest(UUID serviceId, String apiKey, JsonNode payload) {
        MonitoredService service = services.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Serviço não encontrado"));

        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("Header X-API-Key em falta");
        }
        ServiceApiKey key = apiKeys.findByKeyHashAndActiveTrue(ApiKeyGenerator.sha256(apiKey))
                .filter(k -> k.getServiceId().equals(serviceId))
                .orElseThrow(() -> new UnauthorizedException("API key inválida para este serviço"));

        if (!service.isActive()) {
            throw new UnauthorizedException("Serviço está desativado");
        }

        var fields = LogNormalizer.normalize(payload);
        LogEvent event = new LogEvent();
        event.setServiceId(serviceId);
        event.setEventTimestamp(fields.timestamp());
        event.setLevel(fields.level());
        event.setMessage(fields.message());
        event.setEventType(fields.eventType());
        event.setPayload(payload.toString());
        event = logEvents.save(event);

        OffsetDateTime now = OffsetDateTime.now();
        service.setLastSeenAt(now);
        services.save(service);
        key.setLastUsedAt(now);
        apiKeys.save(key);

        return event;
    }
}
