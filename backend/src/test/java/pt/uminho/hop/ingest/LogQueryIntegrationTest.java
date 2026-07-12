package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogQueryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

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

        ingest("{\"level\":\"INFO\",\"message\":\"all good\"}");
        ingest("{\"level\":\"ERROR\",\"message\":\"boom EHR_TIMEOUT\",\"errorCode\":\"EHR_TIMEOUT\"}");
        ingest("{\"level\":\"ERROR\",\"message\":\"another failure\"}");
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    private void ingest(String body) throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void filtersByServiceAndLevel() throws Exception {
        mvc.perform(get("/api/logs")
                        .param("serviceId", serviceId)
                        .param("level", "error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].level").value("ERROR"));
    }

    @Test
    void filtersByTextInPayload() throws Exception {
        // "EHR_TIMEOUT" só existe no payload (campo errorCode) de 1 dos logs
        mvc.perform(get("/api/logs")
                        .param("serviceId", serviceId)
                        .param("text", "ehr_timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].payload", org.hamcrest.Matchers.containsString("EHR_TIMEOUT")));
    }

    @Test
    void paginates() throws Exception {
        mvc.perform(get("/api/logs")
                        .param("serviceId", serviceId)
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void logDetailIncludesServiceNameAndPayload() throws Exception {
        var list = mapper.readTree(mvc.perform(get("/api/logs").param("serviceId", serviceId))
                .andReturn().getResponse().getContentAsString());
        String logId = list.at("/content/0/id").asText();

        mvc.perform(get("/api/logs/" + logId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").isNotEmpty())
                .andExpect(jsonPath("$.payload").isNotEmpty());
    }

    @Test
    void overviewCountsServices() throws Exception {
        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").isNumber())
                .andExpect(jsonPath("$.activeAlerts").value(0))
                .andExpect(jsonPath("$.services").isArray());
    }
}
