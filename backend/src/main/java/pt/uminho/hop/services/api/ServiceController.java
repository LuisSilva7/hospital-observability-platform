package pt.uminho.hop.services.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.services.ServiceManager;
import pt.uminho.hop.services.api.ServiceDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManager manager;

    public ServiceController(ServiceManager manager) {
        this.manager = manager;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCreatedResponse create(@Valid @RequestBody ServiceRequest request) {
        return manager.create(request);
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
        return manager.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public ServiceResponse setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return manager.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        manager.delete(id);
    }

    @PostMapping("/{id}/api-key")
    public ApiKeyResponse regenerateKey(@PathVariable UUID id) {
        return manager.regenerateKey(id);
    }
}
