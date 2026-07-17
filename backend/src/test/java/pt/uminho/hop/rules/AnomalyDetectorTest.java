package pt.uminho.hop.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    @Test
    void emptyHistoryNeverTriggers() {
        assertThat(AnomalyDetector.detect(List.of(), 100, 3)).isEmpty();
    }

    @Test
    void fewEventsNeverTrigger() {
        // menos de 3 erros na janela atual não conta como anomalia
        assertThat(AnomalyDetector.detect(List.of(4L, 4L, 4L), 2, 3)).isEmpty();
    }

    @Test
    void quietHistoryNeverTriggers() {
        // sem linha de base (histórico quase todo a zero) não há anomalia credível
        assertThat(AnomalyDetector.detect(List.of(0L, 0L, 0L, 0L), 5, 3)).isEmpty();
        // uma única janela ativa também não chega
        assertThat(AnomalyDetector.detect(List.of(0L, 0L, 5L, 0L, 0L), 8, 3)).isEmpty();
    }

    @Test
    void constantHistoryUsesStdFloorOfOne() {
        // histórico constante (desvio 0): z degenera para current - mean
        var result = AnomalyDetector.detect(List.of(2L, 2L, 2L, 2L), 6, 3);
        assertThat(result).isPresent();
        assertThat(result.get().zScore()).isEqualTo(4.0);
    }

    @Test
    void noisyHistoryRaisesTheBar() {
        // média 2, desvio 2 (4 janelas ativas): 6 erros dá z=2 (normal); 10 dá z=4 (anomalia)
        List<Long> history = List.of(4L, 0L, 4L, 0L, 4L, 0L, 4L, 0L);
        assertThat(AnomalyDetector.detect(history, 6, 3)).isEmpty();
        var result = AnomalyDetector.detect(history, 10, 3);
        assertThat(result).isPresent();
        assertThat(result.get().zScore()).isEqualTo(4.0);
    }

    @Test
    void currentEqualToHistoryDoesNotTrigger() {
        assertThat(AnomalyDetector.detect(List.of(3L, 3L, 3L, 3L), 3, 3)).isEmpty();
    }
}
