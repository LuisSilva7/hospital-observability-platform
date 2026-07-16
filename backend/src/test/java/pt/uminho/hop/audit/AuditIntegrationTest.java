package pt.uminho.hop.audit;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "hop.llm.api-key=")
@AutoConfigureMockMvc
class AuditIntegrationTest {

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

    /** Procura na 1.ª página do audit uma entrada com esta ação e entityId. */
    private JsonNode findEntry(String entityType, String action, String entityId) throws Exception {
        JsonNode page = mapper.readTree(mvc.perform(get("/api/audit")
                        .param("entityType", entityType)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode entry : page.get("content")) {
            if (action.equals(entry.get("action").asText())
                    && entityId.equals(entry.get("entityId").asText())) {
                return entry;
            }
        }
        return null;
    }

    @Test
    void userAndSystemActionsAreAudited() throws Exception {
        // criação de serviço e regra (USER)
        assertThat(findEntry("SERVICE", "SERVICE_CREATED", serviceId)).isNotNull();
        JsonNode ruleEntry = findEntry("RULE", "RULE_CREATED", ruleId);
        assertThat(ruleEntry).isNotNull();
        assertThat(ruleEntry.get("actor").asText()).isEqualTo("USER");
        assertThat(ruleEntry.at("/details/name").asText()).isEqualTo("Any error");

        // disparo da regra cria alerta (SYSTEM)
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());
        Alert alert = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .findFirst().orElseThrow();
        JsonNode alertEntry = findEntry("ALERT", "ALERT_CREATED", alert.getId().toString());
        assertThat(alertEntry).isNotNull();
        assertThat(alertEntry.get("actor").asText()).isEqualTo("SYSTEM");

        // reconhecimento (USER)
        mvc.perform(post("/api/alerts/" + alert.getId() + "/acknowledge"))
                .andExpect(status().isOk());
        JsonNode ackEntry = findEntry("ALERT", "ALERT_ACKNOWLEDGED", alert.getId().toString());
        assertThat(ackEntry).isNotNull();
        assertThat(ackEntry.get("actor").asText()).isEqualTo("USER");
    }

    @Test
    void auditListPaginatesAndFilters() throws Exception {
        mvc.perform(get("/api/audit").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber());

        // filtro por tipo devolve só esse tipo
        JsonNode page = mapper.readTree(mvc.perform(get("/api/audit")
                        .param("entityType", "RULE").param("size", "50"))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode entry : page.get("content")) {
            assertThat(entry.get("entityType").asText()).isEqualTo("RULE");
        }
    }

    @Test
    void settingsExposeIntegrationStatusWithoutSecrets() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm.configured").value(false))
                .andExpect(jsonPath("$.llm.provider").value("anthropic"))
                .andExpect(jsonPath("$.llm.model").isNotEmpty())
                .andExpect(jsonPath("$.n8n.configured").value(true))
                // nunca expor a chave
                .andExpect(jsonPath("$.llm.apiKey").doesNotExist());
    }
}
