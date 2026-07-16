package pt.uminho.hop.ai.llm;

/**
 * Interface abstrata para fornecedores de LLM (doc de visão, módulo 8).
 * A implementação ativa é escolhida pelo Spring; trocar de fornecedor
 * é acrescentar outra implementação sem tocar no resto do backend.
 */
public interface LlmProvider {

    /** true se o fornecedor tem credenciais configuradas (ex.: LLM_API_KEY). */
    boolean isConfigured();

    /** Nome do fornecedor (ex.: "anthropic"), guardado em ai_analysis.provider. */
    String name();

    /** Identificador do modelo usado (ex.: "claude-sonnet-5"). */
    String model();

    /**
     * Gera a análise estruturada para o prompt dado.
     * @throws LlmException em caso de falha de rede, da API ou de resposta inválida
     */
    AnalysisResult analyze(String systemPrompt, String userContent);
}
