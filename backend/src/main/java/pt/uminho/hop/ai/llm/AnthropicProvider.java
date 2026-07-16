package pt.uminho.hop.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Implementação de LlmProvider para a API da Anthropic (SDK oficial Java).
 * Usa structured outputs para garantir JSON válido com as quatro secções.
 * A chave nunca é guardada na BD — vem só de env var (LLM_API_KEY).
 */
@Component
public class AnthropicProvider implements LlmProvider {

    private static final long MAX_TOKENS = 16000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public AnthropicProvider(@Value("${hop.llm.api-key:}") String apiKey,
                             @Value("${hop.llm.model:claude-sonnet-5}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AnalysisResult analyze(String systemPrompt, String userContent) {
        StructuredMessageCreateParams<AnalysisResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(systemPrompt)
                .outputConfig(AnalysisResult.class)
                .addUserMessage(userContent)
                .build();

        try {
            return client().messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new LlmException("Resposta do modelo sem conteúdo de texto"));
        } catch (AnthropicServiceException e) {
            throw new LlmException("Erro da API Anthropic: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new LlmException("Erro de rede ao contactar a API Anthropic: " + e.getMessage(), e);
        }
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(TIMEOUT)
                            .build();
                }
            }
        }
        return client;
    }
}
