package pt.uminho.hop.automations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AutomationIntegrationTest {

    static HttpServer webhookServer;
    static final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    static final AtomicInteger failuresToServe = new AtomicInteger(0);
    static int port;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String serviceId;
    private String apiKey;
    private String ruleId;

    @BeforeAll
    static void startWebhookServer() throws Exception {
        webhookServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = webhookServer.getAddress().getPort();
        webhookServer.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = failuresToServe.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0 ? 500 : 200;
            if (status == 200) receivedBodies.add(body);
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        webhookServer.start();
    }

    @AfterAll
    static void stopWebhookServer() {
        webhookServer.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        receivedBodies.clear();
        failuresToServe.set(0);

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

    private String createAutomation(String url) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/automations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"ruleId":"%s","name":"Notificar n8n","webhook":{"url":"%s"}}
                                """.formatted(ruleId, url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void alertCreationCallsWebhookWithAlertData() throws Exception {
        createAutomation("http://localhost:" + port + "/hook");

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());

        await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> !receivedBodies.isEmpty());

        var payload = mapper.readTree(receivedBodies.get(0));
        assertThat(payload.get("title").asText()).isEqualTo("Any error");
        assertThat(payload.get("severity").asText()).isEqualTo("HIGH");
        assertThat(payload.get("alertId").asText()).isNotBlank();
    }

    @Test
    void testEndpointExecutesSynchronouslyAndRecordsResult() throws Exception {
        String automationId = createAutomation("http://localhost:" + port + "/hook");

        mvc.perform(post("/api/automations/" + automationId + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.responseCode").value(200));

        assertThat(receivedBodies).hasSize(1);
        assertThat(receivedBodies.get(0)).contains("[TESTE]");
    }

    @Test
    void retriesOnServerErrorAndSucceeds() throws Exception {
        String automationId = createAutomation("http://localhost:" + port + "/hook");
        failuresToServe.set(1); // primeira tentativa falha com 500, a segunda passa

        mvc.perform(post("/api/automations/" + automationId + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.attempts").value(2));
    }

    @Test
    void unreachableUrlFailsAfterAllRetries() throws Exception {
        String automationId = createAutomation("http://localhost:1/unreachable");

        mvc.perform(post("/api/automations/" + automationId + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void disabledAutomationDoesNotFire() throws Exception {
        String automationId = createAutomation("http://localhost:" + port + "/hook");
        mvc.perform(patch("/api/automations/" + automationId + "/enabled")
                        .contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                        .header("X-API-Key", apiKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"level\":\"ERROR\",\"message\":\"boom\"}"))
                .andExpect(status().isCreated());

        Thread.sleep(1500);
        assertThat(receivedBodies).isEmpty();
    }
}
