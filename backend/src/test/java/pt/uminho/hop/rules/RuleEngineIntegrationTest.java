package pt.uminho.hop.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.rules.domain.MonitorRule;
import pt.uminho.hop.rules.repository.MonitorRuleRepository;
import pt.uminho.hop.rules.repository.RuleEvaluationRepository;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RuleEngineIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MonitorRuleRepository rules;
    @Autowired RuleEvaluationRepository evaluations;
    @Autowired MonitoredServiceRepository services;
    @Autowired RuleEngine engine;

    private String serviceId;
    private String apiKey;

    @BeforeEach
    void setUp() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"HIGH",
                                 "expectedIntervalMinutes":1,"toleranceMinutes":0}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        serviceId = created.at("/service/id").asText();
        apiKey = created.at("/apiKey/apiKey").asText();
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    private String createRule(String body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void ingest(String body) throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void eventMatchRuleTriggersOnErrorLogAndRespectsCooldown() throws Exception {
        String ruleId = createRule("""
                {"serviceId":"%s","name":"Any error","type":"EVENT_MATCH","severity":"HIGH",
                 "cooldownMinutes":10,
                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                """.formatted(serviceId));

        ingest("{\"level\":\"INFO\",\"message\":\"ok\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).isEmpty();

        ingest("{\"level\":\"ERROR\",\"message\":\"boom\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).hasSize(1);

        // segundo erro dentro do cooldown não dispara outra vez
        ingest("{\"level\":\"ERROR\",\"message\":\"boom 2\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).hasSize(1);
    }

    @Test
    void countThresholdOnlyTriggersAtThreshold() throws Exception {
        String ruleId = createRule("""
                {"serviceId":"%s","name":"3 erros em 15 min","type":"COUNT_THRESHOLD","severity":"CRITICAL",
                 "windowMinutes":15,"threshold":3,"cooldownMinutes":30,
                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                """.formatted(serviceId));

        ingest("{\"level\":\"ERROR\"}");
        ingest("{\"level\":\"ERROR\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).isEmpty();

        ingest("{\"level\":\"ERROR\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).hasSize(1);
    }

    @Test
    void noActivityRuleTriggersWhenServiceGoesQuiet() throws Exception {
        String ruleId = createRule("""
                {"serviceId":"%s","name":"Silêncio 10 min","type":"NO_ACTIVITY","severity":"CRITICAL",
                 "windowMinutes":10,"cooldownMinutes":30}
                """.formatted(serviceId));

        ingest("{\"level\":\"INFO\"}");

        // "agora" só 5 min depois do último log → não dispara
        engine.checkNoActivity(OffsetDateTime.now().plusMinutes(5));
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).isEmpty();

        // 11 min depois → dispara
        engine.checkNoActivity(OffsetDateTime.now().plusMinutes(11));
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).hasSize(1);

        // e respeita o cooldown na verificação seguinte
        engine.checkNoActivity(OffsetDateTime.now().plusMinutes(12));
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).hasSize(1);
    }

    @Test
    void disabledRuleNeverTriggers() throws Exception {
        String ruleId = createRule("""
                {"serviceId":"%s","name":"Desativada","type":"EVENT_MATCH","severity":"LOW",
                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                """.formatted(serviceId));
        mvc.perform(patch("/api/rules/" + ruleId + "/enabled")
                        .contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        ingest("{\"level\":\"ERROR\"}");
        assertThat(evaluations.findTop50ByRuleIdOrderByTriggeredAtDesc(UUID.fromString(ruleId))).isEmpty();
    }

    @Test
    void ruleValidationRejectsIncompleteRules() throws Exception {
        // EVENT_MATCH sem condições
        mvc.perform(post("/api/rules").contentType(APPLICATION_JSON).content("""
                        {"serviceId":"%s","name":"x","type":"EVENT_MATCH","severity":"LOW"}
                        """.formatted(serviceId)))
                .andExpect(status().isBadRequest());
        // NO_ACTIVITY sem janela
        mvc.perform(post("/api/rules").contentType(APPLICATION_JSON).content("""
                        {"serviceId":"%s","name":"x","type":"NO_ACTIVITY","severity":"LOW"}
                        """.formatted(serviceId)))
                .andExpect(status().isBadRequest());
    }
}
