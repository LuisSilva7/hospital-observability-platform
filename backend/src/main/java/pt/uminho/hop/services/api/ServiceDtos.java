package pt.uminho.hop.services.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.uminho.hop.services.domain.MonitoredService;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ServiceDtos {

    private ServiceDtos() {}

    public record ServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull MonitoredService.Environment environment,
            @NotNull MonitoredService.Criticality criticality,
            @Min(1) Integer expectedIntervalMinutes,
            @Min(0) Integer toleranceMinutes
    ) {}

    public record ServiceResponse(
            UUID id,
            String name,
            String description,
            MonitoredService.Environment environment,
            MonitoredService.Criticality criticality,
            boolean active,
            Integer expectedIntervalMinutes,
            Integer toleranceMinutes,
            OffsetDateTime lastSeenAt,
            OffsetDateTime createdAt,
            String status,
            String ingestEndpoint,
            String apiKeyPrefix
    ) {}

    /** Devolvido apenas na criação do serviço e na regeneração da chave. */
    public record ApiKeyResponse(String apiKey, String prefix, OffsetDateTime createdAt) {}

    public record ServiceCreatedResponse(ServiceResponse service, ApiKeyResponse apiKey) {}
}
