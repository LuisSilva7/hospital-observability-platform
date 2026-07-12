package pt.uminho.hop.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import pt.uminho.hop.services.repository.ServiceApiKeyRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ServiceApiKeyRepository apiKeys;
    @Autowired pt.uminho.hop.services.repository.MonitoredServiceRepository services;

    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        services.findAll().stream()
                .filter(s -> s.getName().startsWith("svc-"))
                .forEach(s -> services.deleteById(s.getId()));
    }

    private String body(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Teste",
                  "environment": "SIMULATION",
                  "criticality": "HIGH",
                  "expectedIntervalMinutes": 5,
                  "toleranceMinutes": 2
                }
                """.formatted(name);
    }

    @Test
    void createReturnsServiceWithPlainKeyOnlyOnce() throws Exception {
        String name = "svc-" + UUID.randomUUID();
        var result = mvc.perform(post("/api/services").contentType(APPLICATION_JSON).content(body(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.name").value(name))
                .andExpect(jsonPath("$.service.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.service.ingestEndpoint").isNotEmpty())
                .andExpect(jsonPath("$.apiKey.apiKey").isNotEmpty())
                .andReturn();

        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        String plainKey = json.at("/apiKey/apiKey").asText();
        String serviceId = json.at("/service/id").asText();
        assertThat(plainKey).startsWith("hop_");

        // GET nunca devolve a chave em claro, só o prefixo
        mvc.perform(get("/api/services/" + serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiKeyPrefix").value(plainKey.substring(0, 12)));

        // Na BD só existe o hash
        var stored = apiKeys.findByKeyHashAndActiveTrue(ApiKeyGenerator.sha256(plainKey));
        assertThat(stored).isPresent();
        assertThat(stored.get().getKeyHash()).isNotEqualTo(plainKey);
    }

    @Test
    void duplicateNameReturnsConflict() throws Exception {
        String name = "svc-" + UUID.randomUUID();
        mvc.perform(post("/api/services").contentType(APPLICATION_JSON).content(body(name)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/services").contentType(APPLICATION_JSON).content(body(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadReturnsValidationError() throws Exception {
        mvc.perform(post("/api/services").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\",\"environment\":\"SIMULATION\",\"criticality\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void regenerateInvalidatesOldKey() throws Exception {
        String name = "svc-" + UUID.randomUUID();
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON).content(body(name)))
                .andReturn().getResponse().getContentAsString());
        String serviceId = created.at("/service/id").asText();
        String oldKey = created.at("/apiKey/apiKey").asText();

        var regenerated = mapper.readTree(mvc.perform(post("/api/services/" + serviceId + "/api-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String newKey = regenerated.at("/apiKey").asText();

        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(apiKeys.findByKeyHashAndActiveTrue(ApiKeyGenerator.sha256(oldKey))).isEmpty();
        assertThat(apiKeys.findByKeyHashAndActiveTrue(ApiKeyGenerator.sha256(newKey))).isPresent();
    }

    @Test
    void updateAndDeleteWork() throws Exception {
        String name = "svc-" + UUID.randomUUID();
        var created = mapper.readTree(mvc.perform(post("/api/services")
                        .contentType(APPLICATION_JSON).content(body(name)))
                .andReturn().getResponse().getContentAsString());
        String serviceId = created.at("/service/id").asText();

        String newName = "svc-" + UUID.randomUUID();
        mvc.perform(put("/api/services/" + serviceId).contentType(APPLICATION_JSON).content(body(newName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));

        mvc.perform(patch("/api/services/" + serviceId + "/active")
                        .contentType(APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(delete("/api/services/" + serviceId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/services/" + serviceId)).andExpect(status().isNotFound());
    }
}
