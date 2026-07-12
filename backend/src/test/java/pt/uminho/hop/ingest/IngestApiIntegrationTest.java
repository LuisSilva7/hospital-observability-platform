package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.ingest.repository.LogEventRepository;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IngestApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LogEventRepository logEvents;
    @Autowired MonitoredServiceRepository services;

    private String serviceId;
    private String apiKey;
    private final java.util.List<String> createdServiceIds = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void cleanup() throws Exception {
        for (String id : createdServiceIds) {
            mvc.perform(delete("/api/services/" + id));
        }
        createdServiceIds.clear();
    }

    @BeforeEach
    void createService() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"HIGH"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        serviceId = created.at("/service/id").asText();
        apiKey = created.at("/apiKey/apiKey").asText();
        createdServiceIds.add(serviceId);
    }

    @Test
    void ingestStoresPayloadAndUpdatesLastSeen() throws Exception {
        String body = """
                {"timestamp":"2026-07-12T14:30:00Z","level":"ERROR",
                 "message":"Failed to send laboratory result",
                 "errorCode":"EHR_TIMEOUT","responseTimeMs":3200}
                """;
        var result = mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        var event = logEvents.findById(UUID.fromString(json.get("id").asText())).orElseThrow();

        assertThat(event.getLevel()).isEqualTo("ERROR");
        assertThat(event.getMessage()).isEqualTo("Failed to send laboratory result");
        // payload original preservado na íntegra, incluindo campos não normalizados
        JsonNode payload = mapper.readTree(event.getPayload());
        assertThat(payload.get("errorCode").asText()).isEqualTo("EHR_TIMEOUT");
        assertThat(payload.get("responseTimeMs").asInt()).isEqualTo(3200);

        var service = services.findById(UUID.fromString(serviceId)).orElseThrow();
        assertThat(service.getLastSeenAt()).isNotNull();
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", "hop_wrongkey")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keyFromAnotherServiceIsRejected() throws Exception {
        var other = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"LOW"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String otherKey = other.at("/apiKey/apiKey").asText();
        createdServiceIds.add(other.at("/service/id").asText());

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", otherKey)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownServiceIs404() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + UUID.randomUUID() + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonObjectBodyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content("[1,2,3]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveServiceIsRejected() throws Exception {
        mvc.perform(patch("/api/services/" + serviceId + "/active")
                        .contentType(APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void differentJsonShapesAreBothStored() throws Exception {
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"nivel\":\"erro\",\"dados\":{\"aninhado\":true}}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"INFO\",\"message\":\"ok\"}"))
                .andExpect(status().isCreated());

        assertThat(logEvents.findTop20ByServiceIdOrderByReceivedAtDesc(UUID.fromString(serviceId)))
                .hasSize(2);
    }
}
