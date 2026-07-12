package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LogNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsAllNormalizedFields() throws Exception {
        var payload = mapper.readTree("""
                {"timestamp":"2026-07-12T14:30:00Z","level":"error",
                 "message":"Failed to send result","eventType":"lab_result","extra":123}
                """);
        var fields = LogNormalizer.normalize(payload);
        assertThat(fields.timestamp()).isEqualTo(OffsetDateTime.parse("2026-07-12T14:30:00Z"));
        assertThat(fields.level()).isEqualTo("ERROR");
        assertThat(fields.message()).isEqualTo("Failed to send result");
        assertThat(fields.eventType()).isEqualTo("lab_result");
    }

    @Test
    void missingFieldsAreNull() throws Exception {
        var fields = LogNormalizer.normalize(mapper.readTree("{\"foo\":\"bar\"}"));
        assertThat(fields.timestamp()).isNull();
        assertThat(fields.level()).isNull();
        assertThat(fields.message()).isNull();
        assertThat(fields.eventType()).isNull();
    }

    @Test
    void normalizesLevelSynonyms() {
        assertThat(LogNormalizer.normalizeLevel("warning")).isEqualTo("WARN");
        assertThat(LogNormalizer.normalizeLevel("critical")).isEqualTo("FATAL");
        assertThat(LogNormalizer.normalizeLevel("Info")).isEqualTo("INFO");
        assertThat(LogNormalizer.normalizeLevel("custom-level")).isEqualTo("CUSTOM-LEVEL");
        assertThat(LogNormalizer.normalizeLevel(null)).isNull();
    }

    @Test
    void invalidTimestampBecomesNull() {
        assertThat(LogNormalizer.parseTimestamp("ontem às 5")).isNull();
        assertThat(LogNormalizer.parseTimestamp(null)).isNull();
    }
}
