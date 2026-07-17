package pt.uminho.hop.ai;

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

@SpringBootTest(properties = "hop.llm.api-key=")
@AutoConfigureMockMvc
class ServiceInsightIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"HIGH"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        serviceId = created.at("/service/id").asText();
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    @Test
    void listStartsEmpty() throws Exception {
        mvc.perform(get("/api/services/" + serviceId + "/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void analyzeWithoutApiKeyReturns503() throws Exception {
        mvc.perform(post("/api/services/" + serviceId + "/analyses"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownServiceReturns404() throws Exception {
        mvc.perform(get("/api/services/" + UUID.randomUUID() + "/analyses"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/services/" + UUID.randomUUID() + "/analyses"))
                .andExpect(status().isNotFound());
    }
}
