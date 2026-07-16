package pt.uminho.hop.ai.llm;

/** Falha ao invocar o fornecedor de LLM (rede, API, resposta inválida). */
public class LlmException extends RuntimeException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }
}
