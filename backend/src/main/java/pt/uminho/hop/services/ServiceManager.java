package pt.uminho.hop.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.uminho.hop.common.ConflictException;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.services.api.ServiceDtos.*;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.domain.ServiceApiKey;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;
import pt.uminho.hop.services.repository.ServiceApiKeyRepository;

import java.util.List;
import java.util.UUID;

@Component
public class ServiceManager {

    private final MonitoredServiceRepository services;
    private final ServiceApiKeyRepository apiKeys;
    private final String publicBaseUrl;

    public ServiceManager(MonitoredServiceRepository services,
                          ServiceApiKeyRepository apiKeys,
                          @Value("${hop.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.services = services;
        this.apiKeys = apiKeys;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public ServiceCreatedResponse create(ServiceRequest request) {
        if (services.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um serviço com o nome '" + request.name() + "'");
        }
        MonitoredService service = new MonitoredService();
        apply(service, request);
        service = services.save(service);

        ApiKeyGenerator.GeneratedKey key = ApiKeyGenerator.generate();
        ServiceApiKey apiKey = new ServiceApiKey();
        apiKey.setServiceId(service.getId());
        apiKey.setKeyHash(key.hash());
        apiKey.setPrefix(key.prefix());
        apiKey = apiKeys.save(apiKey);

        return new ServiceCreatedResponse(
                toResponse(service, apiKey),
                new ApiKeyResponse(key.plainKey(), key.prefix(), apiKey.getCreatedAt())
        );
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return services.findAll().stream()
                .map(s -> toResponse(s, activeKey(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID id) {
        return toResponse(find(id), activeKey(id));
    }

    @Transactional
    public ServiceResponse update(UUID id, ServiceRequest request) {
        MonitoredService service = find(id);
        if (!service.getName().equalsIgnoreCase(request.name())
                && services.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um serviço com o nome '" + request.name() + "'");
        }
        apply(service, request);
        return toResponse(services.save(service), activeKey(id));
    }

    @Transactional
    public ServiceResponse setActive(UUID id, boolean active) {
        MonitoredService service = find(id);
        service.setActive(active);
        return toResponse(services.save(service), activeKey(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!services.existsById(id)) {
            throw new NotFoundException("Serviço não encontrado");
        }
        services.deleteById(id);
    }

    /** Desativa todas as chaves ativas e gera uma nova. A chave em claro só é devolvida aqui. */
    @Transactional
    public ApiKeyResponse regenerateKey(UUID serviceId) {
        find(serviceId);
        apiKeys.findByServiceIdAndActiveTrue(serviceId).forEach(k -> {
            k.setActive(false);
            apiKeys.save(k);
        });
        ApiKeyGenerator.GeneratedKey key = ApiKeyGenerator.generate();
        ServiceApiKey apiKey = new ServiceApiKey();
        apiKey.setServiceId(serviceId);
        apiKey.setKeyHash(key.hash());
        apiKey.setPrefix(key.prefix());
        apiKey = apiKeys.save(apiKey);
        return new ApiKeyResponse(key.plainKey(), key.prefix(), apiKey.getCreatedAt());
    }

    private MonitoredService find(UUID id) {
        return services.findById(id)
                .orElseThrow(() -> new NotFoundException("Serviço não encontrado"));
    }

    private ServiceApiKey activeKey(UUID serviceId) {
        return apiKeys.findFirstByServiceIdAndActiveTrueOrderByCreatedAtDesc(serviceId).orElse(null);
    }

    private void apply(MonitoredService service, ServiceRequest request) {
        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setEnvironment(request.environment());
        service.setCriticality(request.criticality());
        service.setExpectedIntervalMinutes(request.expectedIntervalMinutes());
        service.setToleranceMinutes(request.toleranceMinutes());
    }

    /**
     * Deriva o estado do serviço a partir do último log recebido e do
     * intervalo esperado: INACTIVE (desativado), UNKNOWN (nunca recebeu),
     * SILENT (sem logs há mais de intervalo+tolerância) ou HEALTHY.
     */
    public static String deriveStatus(MonitoredService s, java.time.OffsetDateTime now) {
        if (!s.isActive()) return "INACTIVE";
        if (s.getLastSeenAt() == null) return "UNKNOWN";
        if (s.getExpectedIntervalMinutes() != null) {
            long allowedMinutes = s.getExpectedIntervalMinutes()
                    + (s.getToleranceMinutes() == null ? 0 : s.getToleranceMinutes());
            if (s.getLastSeenAt().plusMinutes(allowedMinutes).isBefore(now)) {
                return "SILENT";
            }
        }
        return "HEALTHY";
    }

    private ServiceResponse toResponse(MonitoredService s, ServiceApiKey key) {
        String status = deriveStatus(s, java.time.OffsetDateTime.now());
        return new ServiceResponse(
                s.getId(), s.getName(), s.getDescription(), s.getEnvironment(),
                s.getCriticality(), s.isActive(), s.getExpectedIntervalMinutes(),
                s.getToleranceMinutes(), s.getLastSeenAt(), s.getCreatedAt(), status,
                publicBaseUrl + "/api/v1/ingest/" + s.getId() + "/logs",
                key == null ? null : key.getPrefix()
        );
    }
}
