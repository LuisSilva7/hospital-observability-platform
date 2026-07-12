package pt.uminho.hop.ingest.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.common.NotFoundException;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/logs")
public class LogQueryController {

    private final LogEventRepository logEvents;
    private final MonitoredServiceRepository services;

    public LogQueryController(LogEventRepository logEvents, MonitoredServiceRepository services) {
        this.logEvents = logEvents;
        this.services = services;
    }

    public record LogEventResponse(
            UUID id, UUID serviceId, String serviceName,
            OffsetDateTime receivedAt, OffsetDateTime eventTimestamp,
            String level, String message, String eventType, String payload) {}

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Specification<LogEvent> spec = (root, query, cb) -> cb.conjunction();
        if (serviceId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("serviceId"), serviceId));
        }
        if (level != null && !level.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("level"), level.trim().toUpperCase()));
        }
        if (from != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("receivedAt"), from));
        }
        if (to != null) {
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("receivedAt"), to));
        }
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("message")), pattern),
                    cb.like(cb.lower(r.get("payloadText")), pattern)
            ));
        }

        Page<LogEvent> result = logEvents.findAll(spec,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "receivedAt")));

        Map<UUID, String> serviceNames = services.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));

        Function<LogEvent, LogEventResponse> toDto = e -> new LogEventResponse(
                e.getId(), e.getServiceId(),
                serviceNames.getOrDefault(e.getServiceId(), "(removido)"),
                e.getReceivedAt(), e.getEventTimestamp(),
                e.getLevel(), e.getMessage(), e.getEventType(), e.getPayload());

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent().stream().map(toDto).toList());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return response;
    }

    @GetMapping("/{id}")
    public LogEventResponse get(@PathVariable UUID id) {
        LogEvent e = logEvents.findById(id)
                .orElseThrow(() -> new NotFoundException("Log não encontrado"));
        String serviceName = services.findById(e.getServiceId())
                .map(s -> s.getName()).orElse("(removido)");
        return new LogEventResponse(
                e.getId(), e.getServiceId(), serviceName,
                e.getReceivedAt(), e.getEventTimestamp(),
                e.getLevel(), e.getMessage(), e.getEventType(), e.getPayload());
    }
}
