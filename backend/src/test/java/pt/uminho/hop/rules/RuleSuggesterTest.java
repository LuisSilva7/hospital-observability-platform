package pt.uminho.hop.rules;

import org.junit.jupiter.api.Test;
import pt.uminho.hop.services.domain.MonitoredService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleSuggesterTest {

    private MonitoredService service(String name, UUID id) {
        MonitoredService svc = mock(MonitoredService.class);
        when(svc.getName()).thenReturn(name);
        when(svc.getId()).thenReturn(id);
        return svc;
    }

    @Test
    void resolvesServiceByExactNameIgnoringCase() {
        UUID labId = UUID.randomUUID();
        var all = List.of(service("Laboratory Integration Service", labId),
                service("Notification Service", UUID.randomUUID()));

        assertThat(RuleSuggester.resolveService(all, "laboratory integration service"))
                .isEqualTo(labId);
    }

    @Test
    void resolvesServiceByPartialMatch() {
        UUID labId = UUID.randomUUID();
        var all = List.of(service("Laboratory Integration Service", labId));

        assertThat(RuleSuggester.resolveService(all, "Laboratory")).isEqualTo(labId);
        assertThat(RuleSuggester.resolveService(all, "o laboratory integration service completo"))
                .isEqualTo(labId);
    }

    @Test
    void unknownOrBlankNameResolvesToNull() {
        var all = List.of(service("Laboratory Integration Service", UUID.randomUUID()));

        assertThat(RuleSuggester.resolveService(all, "Faturação")).isNull();
        assertThat(RuleSuggester.resolveService(all, "")).isNull();
        assertThat(RuleSuggester.resolveService(all, null)).isNull();
    }
}
