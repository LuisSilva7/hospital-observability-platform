package pt.uminho.hop.rules;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "hop.llm.api-key=")
@AutoConfigureMockMvc
class RuleSuggestIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void suggestWithoutApiKeyReturns503() throws Exception {
        mvc.perform(post("/api/rules/suggest")
                        .contentType(APPLICATION_JSON)
                        .content("{\"prompt\":\"avisa-me se o laboratório enviar mais de 5 erros em 15 minutos\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void blankPromptReturns400() throws Exception {
        mvc.perform(post("/api/rules/suggest")
                        .contentType(APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
