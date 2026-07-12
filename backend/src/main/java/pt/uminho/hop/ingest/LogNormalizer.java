package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Extrai os campos normalizados (timestamp, level, message, eventType) de um
 * payload JSON arbitrário. Nenhum campo é obrigatório: o payload original é
 * sempre preservado na íntegra; a normalização serve apenas filtros e regras.
 */
public final class LogNormalizer {

    private static final Set<String> KNOWN_LEVELS =
            Set.of("TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "FATAL", "CRITICAL");

    private LogNormalizer() {}

    public record NormalizedFields(OffsetDateTime timestamp, String level, String message, String eventType) {}

    public static NormalizedFields normalize(JsonNode payload) {
        return new NormalizedFields(
                parseTimestamp(text(payload, "timestamp")),
                normalizeLevel(text(payload, "level")),
                truncate(text(payload, "message"), 10_000),
                truncate(text(payload, "eventType"), 120)
        );
    }

    static OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(raw).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    static String normalizeLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String level = raw.trim().toUpperCase();
        if (level.equals("WARNING")) level = "WARN";
        if (level.equals("CRITICAL")) level = "FATAL";
        return KNOWN_LEVELS.contains(level) ? level : truncate(level, 20);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
