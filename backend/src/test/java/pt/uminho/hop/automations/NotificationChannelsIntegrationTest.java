package pt.uminho.hop.automations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Canais EMAIL e TEAMS das automações (extensão E12). */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationChannelsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String serviceId;
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
    void emailAutomationWithoutSmtpFailsWithClearMessage() throws Exception {
        String id = mapper.readTree(mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Avisar por email","type":"EMAIL",
                                 "email":{"to":"equipa@hospital.pt"}}
                                """.formatted(ruleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.email.to").value("equipa@hospital.pt"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // sem SMTP_HOST configurado no ambiente de teste
        mvc.perform(post("/api/automations/" + id + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value(containsString("SMTP não configurado")));
    }

    @Test
    void teamsAutomationSendsCardAndFailsOnUnreachableUrl() throws Exception {
        String id = mapper.readTree(mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Cartão no Teams","type":"TEAMS",
                                 "teams":{"url":"http://localhost:9/webhook-inexistente"}}
                                """.formatted(ruleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEAMS"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // porta fechada → falha após retries, com attempts registados
        mvc.perform(post("/api/automations/" + id + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(3));
    }

    @Test
    void channelConfigsAreValidated() throws Exception {
        mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Email sem destinatários","type":"EMAIL"}
                                """.formatted(ruleId)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Teams sem URL","type":"TEAMS"}
                                """.formatted(ruleId)))
                .andExpect(status().isBadRequest());
    }
}
