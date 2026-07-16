package pt.uminho.hop.services.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.audit.AuditTrail;
import pt.uminho.hop.services.ServiceManager;
import pt.uminho.hop.services.api.ServiceDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManager manager;
    private final AuditTrail audit;

    public ServiceController(ServiceManager manager, AuditTrail audit) {
        this.manager = manager;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCreatedResponse create(@Valid @RequestBody ServiceRequest request) {
        ServiceCreatedResponse created = manager.create(request);
        audit.user("SERVICE_CREATED", "SERVICE", created.service().id(),
                Map.of("name", created.service().name()));
        return created;
    }

    @GetMapping
    public List<ServiceResponse> list() {
        return manager.list();
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable UUID id) {
        return manager.get(id);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        ServiceResponse updated = manager.update(id, request);
        audit.user("SERVICE_UPDATED", "SERVICE", id, Map.of("name", updated.name()));
        return updated;
    }

    @PatchMapping("/{id}/active")
    public ServiceResponse setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        ServiceResponse updated = manager.setActive(id, active);
        audit.user(active ? "SERVICE_ACTIVATED" : "SERVICE_DEACTIVATED", "SERVICE", id,
                Map.of("name", updated.name()));
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        String name = manager.get(id).name();
        manager.delete(id);
        audit.user("SERVICE_DELETED", "SERVICE", id, Map.of("name", name));
    }

    @PostMapping("/{id}/api-key")
    public ApiKeyResponse regenerateKey(@PathVariable UUID id) {
        ApiKeyResponse key = manager.regenerateKey(id);
        // nunca registar a chave em claro — só o prefixo visível
        audit.user("SERVICE_KEY_REGENERATED", "SERVICE", id, Map.of("prefix", key.prefix()));
        return key;
    }
}
