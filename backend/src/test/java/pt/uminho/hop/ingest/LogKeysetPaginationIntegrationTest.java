package pt.uminho.hop.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogKeysetPaginationIntegrationTest {

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

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/ingest/" + serviceId + "/logs")
                            .header("X-API-Key", apiKey)
                            .contentType(APPLICATION_JSON)
                            .content("{\"level\":\"INFO\",\"message\":\"log " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        mvc.perform(delete("/api/services/" + serviceId));
    }

    private JsonNode fetch(String cursor) throws Exception {
        var request = get("/api/logs").param("serviceId", serviceId).param("size", "2");
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        return mapper.readTree(mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void cursorWalksAllLogsWithoutOverlapOrCounts() throws Exception {
        List<String> seenIds = new ArrayList<>();

        // 1.ª página: modo com contagem + nextCursor
        JsonNode first = fetch(null);
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("content")).hasSize(2);
        first.get("content").forEach(l -> seenIds.add(l.get("id").asText()));
        String cursor = first.get("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        // 2.ª página (keyset): sem contagens
        JsonNode second = fetch(cursor);
        assertThat(second.has("totalElements")).isFalse();
        assertThat(second.get("content")).hasSize(2);
        second.get("content").forEach(l -> seenIds.add(l.get("id").asText()));
        assertThat(second.get("nextCursor").asText()).isNotBlank();

        // 3.ª página: último log, sem nextCursor
        JsonNode third = fetch(second.get("nextCursor").asText());
        assertThat(third.get("content")).hasSize(1);
        third.get("content").forEach(l -> seenIds.add(l.get("id").asText()));
        assertThat(third.get("nextCursor").isNull()).isTrue();

        // sem repetidos nem saltos
        assertThat(seenIds).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void invalidCursorReturns400() throws Exception {
        mvc.perform(get("/api/logs").param("cursor", "lixo"))
                .andExpect(status().isBadRequest());
    }
}
