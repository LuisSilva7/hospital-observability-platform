package pt.uminho.hop.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SimulatorControlIntegrationTest {

    @Autowired MockMvc mvc;

    private static final String STATUS_BODY = """
            {"profiles":[
              {"profile":"laboratory","serviceName":"Lab","serviceId":null,
               "scenario":"normal","intervalSeconds":6}
            ]}
            """;

    @Test
    void statusReportMakesSimulatorConnectedAndScenarioChangesFlowBack() throws Exception {
        // 1) simulador reporta estado → ligado, sem alterações pendentes
        mvc.perform(post("/api/simulator/status")
                        .contentType(APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios").isEmpty());

        mvc.perform(get("/api/simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.profiles[0].profile").value("laboratory"))
                .andExpect(jsonPath("$.profiles[0].scenario").value("normal"));

        // 2) UI pede pico de erros → fica pendente
        mvc.perform(put("/api/simulator/scenarios/laboratory")
                        .contentType(APPLICATION_JSON)
                        .content("{\"scenario\":\"error-spike\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[0].pendingScenario").value("error-spike"));

        // 3) próximo sync do simulador recebe a alteração
        mvc.perform(post("/api/simulator/status")
                        .contentType(APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(jsonPath("$.scenarios.laboratory").value("error-spike"));

        // 4) quando o simulador reporta o cenário aplicado, deixa de estar pendente
        mvc.perform(post("/api/simulator/status")
                        .contentType(APPLICATION_JSON)
                        .content(STATUS_BODY.replace("\"normal\"", "\"error-spike\"")))
                .andExpect(jsonPath("$.scenarios").isEmpty());
        mvc.perform(get("/api/simulator"))
                .andExpect(jsonPath("$.profiles[0].scenario").value("error-spike"))
                .andExpect(jsonPath("$.profiles[0].pendingScenario").doesNotExist());
    }

    @Test
    void invalidScenarioAndUnknownProfileAreRejected() throws Exception {
        mvc.perform(post("/api/simulator/status")
                        .contentType(APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(status().isOk());

        mvc.perform(put("/api/simulator/scenarios/laboratory")
                        .contentType(APPLICATION_JSON)
                        .content("{\"scenario\":\"caos-total\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/simulator/scenarios/faturacao")
                        .contentType(APPLICATION_JSON)
                        .content("{\"scenario\":\"normal\"}"))
                .andExpect(status().isNotFound());
    }
}
