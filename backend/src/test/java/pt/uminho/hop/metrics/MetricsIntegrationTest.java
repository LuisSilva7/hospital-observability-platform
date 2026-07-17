package pt.uminho.hop.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.repository.AlertRepository;
import pt.uminho.hop.metrics.MetricsController.Stat;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AlertRepository alerts;

    private String serviceId;
    private String apiKey;
    private String ruleId;

    @BeforeEach
    void setUp() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"HIGH"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        serviceId = created.at("/service/id").asText();
        apiKey = created.at("/apiKey/apiKey").asText();

        ruleId = mapper.readTree(mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","name":"Any error","type":"EVENT_MATCH","severity":"HIGH",
                                 "cooldownMinutes":0,
                                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                                """.formatted(serviceId)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    @Test
    void metricsReflectAlertLifecycle() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());

        Alert alert = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .findFirst().orElseThrow();
        mvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge")).andExpect(status().isOk());
        mvc.perform(post("/api/alerts/" + alert.getId() + "/resolve")).andExpect(status().isOk());

        JsonNode metrics = mapper.readTree(mvc.perform(get("/api/metrics").param("days", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // BD partilhada: asserções com >= para não depender de estado limpo
        assertThat(metrics.at("/windowDays").asInt()).isEqualTo(1);
        assertThat(metrics.at("/counts/alerts").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.at("/counts/resolvedAlerts").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.at("/counts/logs").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.at("/detection/count").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.at("/detection/avgMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.at("/mtta/count").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.at("/mttr/count").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void statComputesPercentilesAndAverage() {
        Stat stat = Stat.of(List.of(10L, 20L, 30L, 40L, 100L));
        assertThat(stat.count()).isEqualTo(5);
        assertThat(stat.avgMs()).isEqualTo(40);
        assertThat(stat.p50Ms()).isEqualTo(30);
        assertThat(stat.p95Ms()).isEqualTo(100);
        assertThat(stat.maxMs()).isEqualTo(100);

        Stat empty = Stat.of(List.of());
        assertThat(empty.count()).isZero();
        assertThat(empty.avgMs()).isNull();
    }
}
