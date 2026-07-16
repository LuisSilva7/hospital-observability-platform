package pt.uminho.hop.ai.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Estrutura das quatro secções pedidas ao LLM (também é o schema do structured output). */
public record AnalysisResult(
        @JsonPropertyDescription("Resumo do incidente em 2-3 frases, em português europeu")
        String summary,
        @JsonPropertyDescription("Causa provável do incidente, deduzida apenas dos logs fornecidos; indicar incerteza quando existir")
        String probableCause,
        @JsonPropertyDescription("Evidências concretas citadas dos logs fornecidos (timestamp e mensagem)")
        List<String> evidence,
        @JsonPropertyDescription("Recomendações de próximos passos para o operador humano (sugestões, não ações executadas)")
        List<String> recommendations) {
}
