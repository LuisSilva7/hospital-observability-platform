package pt.uminho.hop.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.alerts.domain.Alert;
import pt.uminho.hop.alerts.repository.AlertRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnomalyRuleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RuleEngine engine;
    @Autowired AlertRepository alerts;
    @Autowired JdbcTemplate jdbc;

    private String serviceId;
    private String apiKey;

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
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    private String createAnomalyRule() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","name":"Erros anómalos","type":"ANOMALY",
                                 "severity":"HIGH","windowMinutes":5,"threshold":3,"cooldownMinutes":0}
                                """.formatted(serviceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void anomalyRuleRequiresWindow() throws Exception {
        mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","name":"Sem janela","type":"ANOMALY","severity":"HIGH"}
                                """.formatted(serviceId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorSpikeAboveHistoryTriggersAlert() throws Exception {
        String ruleId = createAnomalyRule();

        // janela atual: 8 erros agora (acima do MIN_EVENTS e bem acima da linha de base)
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                            .header("X-API-Key", apiKey)
                            .contentType(APPLICATION_JSON)
                            .content("{\"level\":\"ERROR\",\"message\":\"burst " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        // linha de base: 1 erro em cada uma de 3 janelas antigas distintas
        // (janela = 5 min → -7, -12, -17 min caem em buckets diferentes)
        for (int minutesAgo : new int[]{7, 12, 17}) {
            mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                            .header("X-API-Key", apiKey)
                            .contentType(APPLICATION_JSON)
                            .content("{\"level\":\"ERROR\",\"message\":\"hist-" + minutesAgo + "\"}"))
                    .andExpect(status().isCreated());
            jdbc.update("update log_event set received_at = now() - make_interval(mins => ?) "
                            + "where service_id = ? and message = ?",
                    minutesAgo, UUID.fromString(serviceId), "hist-" + minutesAgo);
        }

        engine.checkAnomalies(OffsetDateTime.now());

        Alert alert = alerts.findAll().stream()
                .filter(a -> UUID.fromString(ruleId).equals(a.getRuleId()))
                .findFirst().orElse(null);
        assertThat(alert).isNotNull();
    }

    @Test
    void normalErrorRateDoesNotTrigger() throws Exception {
        String ruleId = createAnomalyRule();

        // só 2 erros na janela atual — abaixo do mínimo de 3 eventos
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                            .header("X-API-Key", apiKey)
                            .contentType(APPLICATION_JSON)
                            .content("{\"level\":\"ERROR\",\"message\":\"poucos " + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        engine.checkAnomalies(OffsetDateTime.now());

        assertThat(alerts.findAll().stream()
                .anyMatch(a -> UUID.fromString(ruleId).equals(a.getRuleId()))).isFalse();
    }
}
