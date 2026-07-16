package pt.uminho.hop.ai;

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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// api-key forçada a vazio para o teste ser determinístico mesmo com LLM_API_KEY no ambiente
@SpringBootTest(properties = "hop.llm.api-key=")
@AutoConfigureMockMvc
class AIAnalysisIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AlertRepository alerts;

    private String serviceId;
    private String apiKey;
    private UUID alertId;

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

        String ruleId = mapper.readTree(mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","name":"Any error","type":"EVENT_MATCH","severity":"HIGH",
                                 "cooldownMinutes":0,
                                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                                """.formatted(serviceId)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());

        alertId = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .map(Alert::getId)
                .findFirst().orElseThrow();
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    @Test
    void listStartsEmpty() throws Exception {
        mvc.perform(get("/api/alerts/" + alertId + "/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void analyzeWithoutApiKeyReturns503() throws Exception {
        mvc.perform(post("/api/alerts/" + alertId + "/analyses"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownAlertReturns404() throws Exception {
        mvc.perform(get("/api/alerts/" + UUID.randomUUID() + "/analyses"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/alerts/" + UUID.randomUUID() + "/analyses"))
                .andExpect(status().isNotFound());
    }
}
