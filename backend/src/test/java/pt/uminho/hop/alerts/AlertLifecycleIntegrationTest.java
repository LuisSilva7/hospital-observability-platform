package pt.uminho.hop.alerts;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AlertLifecycleIntegrationTest {

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

        // cooldown 0 para poder testar disparos repetidos
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

    private void ingestError() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());
    }

    private long countAlertsForRule() {
        return alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .count();
    }

    @Test
    void triggerCreatesAlertAndRepeatedTriggersAttachToIt() throws Exception {
        ingestError();
        assertThat(countAlertsForRule()).isEqualTo(1);

        // segundo disparo (cooldown 0) NÃO cria novo alerta — anexa ao existente
        ingestError();
        assertThat(countAlertsForRule()).isEqualTo(1);

        Alert alert = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .findFirst().orElseThrow();

        var detail = mapper.readTree(mvc.perform(get("/api/alerts/" + alert.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // timeline: CREATED + TRIGGER_REPEATED; 2 logs associados
        assertThat(detail.at("/alert/status").asText()).isEqualTo("OPEN");
        assertThat(detail.get("timeline")).hasSize(2);
        assertThat(detail.at("/timeline/0/type").asText()).isEqualTo("CREATED");
        assertThat(detail.at("/timeline/1/type").asText()).isEqualTo("TRIGGER_REPEATED");
        assertThat(detail.get("logs")).hasSize(2);
    }

    @Test
    void acknowledgeAndResolveFollowLifecycle() throws Exception {
        ingestError();
        Alert alert = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .findFirst().orElseThrow();

        mvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());

        // reconhecer duas vezes → 409
        mvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/alerts/" + alert.getId() + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        // depois de resolvido, novo disparo cria alerta NOVO
        ingestError();
        assertThat(countAlertsForRule()).isEqualTo(2);
    }

    @Test
    void listFiltersByStatus() throws Exception {
        ingestError();
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ruleId == '" + ruleId + "')]").isNotEmpty());
        mvc.perform(get("/api/alerts").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ruleId == '" + ruleId + "')]").isEmpty());
    }

    @Test
    void overviewCountsActiveAlerts() throws Exception {
        ingestError();
        var overview = mapper.readTree(mvc.perform(get("/api/overview"))
                .andReturn().getResponse().getContentAsString());
        assertThat(overview.get("activeAlerts").asLong()).isGreaterThanOrEqualTo(1);
    }
}
