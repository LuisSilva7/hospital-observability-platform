package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.ingest.domain.LogEvent;
import pt.uminho.hop.ingest.repository.LogEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retenção com 3650 dias para nunca apagar dados reais da BD de dev partilhada;
 * os logs do teste são recuados para lá do cutoff via SQL.
 */
@SpringBootTest(properties = "hop.retention.log-days=3650")
@AutoConfigureMockMvc
class LogRetentionJobIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LogRetentionJob job;
    @Autowired LogEventRepository logEvents;
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

        mvc.perform(post("/api/rules")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","name":"Any error","type":"EVENT_MATCH","severity":"HIGH",
                                 "cooldownMinutes":0,
                                 "conditions":[{"fieldPath":"level","operator":"EQUALS","expectedValue":"ERROR"}]}
                                """.formatted(serviceId)))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    private void ingest(String level, String message) throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"%s\",\"message\":\"%s\"}".formatted(level, message)))
                .andExpect(status().isCreated());
    }

    @Test
    void purgesOldUnlinkedLogsButKeepsAlertEvidence() throws Exception {
        ingest("ERROR", "boom");      // dispara a regra → fica ligado ao alerta
        ingest("INFO", "ruido");      // sem ligação — deve ser apagado

        // recuar ambos para lá do cutoff (3650 dias)
        jdbc.update("update log_event set received_at = now() - interval '3651 days' where service_id = ?",
                UUID.fromString(serviceId));

        job.purge();

        List<LogEvent> remaining = logEvents.findTop20ByServiceIdOrderByReceivedAtDesc(
                UUID.fromString(serviceId));
        assertThat(remaining).extracting(LogEvent::getMessage).containsExactly("boom");

        // auditoria regista a limpeza como ação do sistema
        var audit = mapper.readTree(mvc.perform(get("/api/audit")
                        .param("entityType", "LOG").param("size", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(audit.at("/content/0/action").asText()).isEqualTo("LOGS_PURGED");
        assertThat(audit.at("/content/0/actor").asText()).isEqualTo("SYSTEM");
        assertThat(audit.at("/content/0/details/deleted").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void disabledRetentionDeletesNothing() throws Exception {
        ingest("INFO", "fica");
        jdbc.update("update log_event set received_at = now() - interval '3651 days' where service_id = ?",
                UUID.fromString(serviceId));

        // instância com retenção desativada (0 dias)
        new LogRetentionJob(logEvents, null, null, 0).purge();

        assertThat(logEvents.findTop20ByServiceIdOrderByReceivedAtDesc(UUID.fromString(serviceId)))
                .extracting(LogEvent::getMessage).containsExactly("fica");
    }
}
