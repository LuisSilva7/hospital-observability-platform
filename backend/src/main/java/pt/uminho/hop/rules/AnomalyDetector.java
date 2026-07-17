package pt.uminho.hop.rules;

import java.util.List;
import java.util.Optional;

/**
 * Deteção estatística simples (extensão E9): z-score da contagem de erros na
 * janela atual face ao histórico das janelas anteriores. Sem ML externo — só
 * média e desvio-padrão sobre o que está na base de dados.
 */
public final class AnomalyDetector {

    /** Mínimo de eventos na janela atual para considerar anomalia (evita ruído de 1-2 erros). */
    static final int MIN_EVENTS = 3;

    /**
     * Mínimo de janelas de histórico COM atividade para haver uma linha de base
     * credível. Sem isto, um serviço novo ou de baixo tráfego (histórico quase
     * todo a zero) dispararia logo no 1.º pico com z ≈ current/1.
     */
    static final int MIN_NONZERO_HISTORY = 3;

    private AnomalyDetector() {}

    public record Result(double zScore, double mean, double stdDev, long current) {}

    /**
     * @param historyCounts contagens de erros das janelas anteriores (histórico)
     * @param current       contagem de erros na janela atual
     * @param minZ          limiar de z-score a partir do qual é anomalia
     * @return resultado se for anomalia; vazio caso contrário
     *
     * O desvio-padrão tem um mínimo de 1.0: com histórico constante (desvio 0)
     * o z-score degenera para "current - mean", mantendo o detetor utilizável
     * sem divisões por zero nem disparos por variações mínimas.
     */
    public static Optional<Result> detect(List<Long> historyCounts, long current, double minZ) {
        long nonZeroBuckets = historyCounts.stream().filter(c -> c > 0).count();
        if (current < MIN_EVENTS || nonZeroBuckets < MIN_NONZERO_HISTORY) {
            return Optional.empty();
        }
        double mean = historyCounts.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = historyCounts.stream()
                .mapToDouble(c -> (c - mean) * (c - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double z = (current - mean) / Math.max(stdDev, 1.0);
        return z >= minZ
                ? Optional.of(new Result(z, mean, stdDev, current))
                : Optional.empty();
    }
}
