package pt.uminho.hop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa a redação de campos sensíveis e a truncagem de payloads do AIAnalyzer. */
class PayloadSanitizerTest {

    private AIAnalyzer analyzer(String redactedFields) {
        return new AIAnalyzer(null, null, null, null, null, null, null,
                new ObjectMapper(), null, null, redactedFields);
    }

    @Test
    void redactsConfiguredFieldsCaseInsensitiveAndNested() {
        String payload = """
                {"level":"ERROR","PatientId":"12345","details":{"patientid":"999","code":"X"},
                 "items":[{"patientId":"1"}]}
                """;
        String result = analyzer("patientId,nif").sanitizePayload(payload);

        assertThat(result).doesNotContain("12345").doesNotContain("999");
        assertThat(result).contains("[removido]");
        assertThat(result).contains("\"code\":\"X\"");
    }

    @Test
    void keepsPayloadIntactWhenNoFieldsConfigured() {
        String payload = "{\"patientId\":\"12345\"}";
        assertThat(analyzer("").sanitizePayload(payload)).isEqualTo(payload);
    }

    @Test
    void truncatesLongPayloads() {
        String payload = "{\"data\":\"" + "x".repeat(5000) + "\"}";
        String result = analyzer("").sanitizePayload(payload);
        assertThat(result.length()).isLessThanOrEqualTo(AIAnalyzer.MAX_PAYLOAD_CHARS + 20);
        assertThat(result).endsWith("…(truncado)");
    }

    @Test
    void nonJsonPayloadIsStillTruncatedWhenRedactionActive() {
        String payload = "not-json " + "y".repeat(5000);
        String result = analyzer("secret").sanitizePayload(payload);
        assertThat(result).endsWith("…(truncado)");
    }
}
