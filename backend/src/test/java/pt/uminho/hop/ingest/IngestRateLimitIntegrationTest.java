package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "hop.ingest.rate-limit-per-minute=5")
@AutoConfigureMockMvc
class IngestRateLimitIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private final List<String> createdServices = new ArrayList<>();

    private record Svc(String id, String key) {}

    private Svc createService() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"svc-%s","environment":"SIMULATION","criticality":"HIGH"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String id = created.at("/service/id").asText();
        createdServices.add(id);
        return new Svc(id, created.at("/apiKey/apiKey").asText());
    }

    @AfterEach
    void cleanup() throws Exception {
        for (String id : createdServices) {
            mvc.perform(delete("/api/services/" + id));
        }
    }

    @Test
    void limitIsPerServiceAndReturns429WithRetryAfter() throws Exception {
        Svc svc = createService();

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/ingest/" + svc.id() + "/logs")
                            .header("X-API-Key", svc.key())
                            .contentType(APPLICATION_JSON)
                            .content("{\"level\":\"INFO\",\"message\":\"ok " + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        // 6.º pedido excede o bucket (capacidade 5/min)
        mvc.perform(post("/api/v1/ingest/" + svc.id() + "/logs")
                        .header("X-API-Key", svc.key())
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"INFO\",\"message\":\"excede\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));

        // outro serviço tem bucket próprio — não é afetado
        Svc other = createService();
        mvc.perform(post("/api/v1/ingest/" + other.id() + "/logs")
                        .header("X-API-Key", other.key())
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"INFO\",\"message\":\"independente\"}"))
                .andExpect(status().isCreated());
    }
}
