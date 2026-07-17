package pt.uminho.hop.automations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Ação de automação AI_ANALYSIS: análise de IA automática quando o alerta é criado. */
@SpringBootTest(properties = "hop.llm.api-key=")
@AutoConfigureMockMvc
class AiAnalysisAutomationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

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

    private String createAiAutomation() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Analisar automaticamente","type":"AI_ANALYSIS"}
                                """.formatted(ruleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("AI_ANALYSIS"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void webhookAutomationStillRequiresWebhookConfig() throws Exception {
        mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Sem webhook"}
                                """.formatted(ruleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertCreationTriggersAutomaticAnalysis() throws Exception {
        String automationId = createAiAutomation();

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());

        // executor é assíncrono; sem LLM_API_KEY a execução fica FAILED com mensagem clara
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode executions = mapper.readTree(mvc.perform(
                            get("/api/automations/" + automationId + "/executions"))
                    .andReturn().getResponse().getContentAsString());
            assertThat(executions).isNotEmpty();
            assertThat(executions.at("/0/status").asText()).isEqualTo("FAILED");
            assertThat(executions.at("/0/error").asText()).contains("não configurada");
        });
    }

    @Test
    void testEndpointWithoutRealAlertExplainsWhy() throws Exception {
        String automationId = createAiAutomation();
        mvc.perform(post("/api/automations/" + automationId + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("alerta real")));
    }
}
