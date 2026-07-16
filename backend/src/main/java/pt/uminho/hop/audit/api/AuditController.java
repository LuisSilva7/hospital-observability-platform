package pt.uminho.hop.audit.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import pt.uminho.hop.audit.domain.AuditEntry;
import pt.uminho.hop.audit.repository.AuditEntryRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEntryRepository entries;
    private final ObjectMapper mapper;

    public AuditController(AuditEntryRepository entries, ObjectMapper mapper) {
        this.entries = entries;
        this.mapper = mapper;
    }

    public record AuditEntryResponse(
            UUID id, String action, String entityType, UUID entityId,
            AuditEntry.Actor actor, JsonNode details, OffsetDateTime createdAt) {}

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEntry> result = entityType == null || entityType.isBlank()
                ? entries.findAll(pageable)
                : entries.findByEntityType(entityType.trim().toUpperCase(), pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent().stream().map(this::toResponse).toList());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return response;
    }

    private AuditEntryResponse toResponse(AuditEntry e) {
        JsonNode details = null;
        if (e.getDetails() != null) {
            try {
                details = mapper.readTree(e.getDetails());
            } catch (Exception ignored) {
            }
        }
        return new AuditEntryResponse(e.getId(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getActor(), details, e.getCreatedAt());
    }
}
