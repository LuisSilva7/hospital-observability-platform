package pt.uminho.hop.services;

import org.junit.jupiter.api.Test;
import pt.uminho.hop.services.domain.MonitoredService;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceStatusTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-07-12T12:00:00Z");

    private MonitoredService service(boolean active, OffsetDateTime lastSeen,
                                     Integer interval, Integer tolerance) {
        var s = new MonitoredService();
        s.setName("test");
        s.setEnvironment(MonitoredService.Environment.SIMULATION);
        s.setCriticality(MonitoredService.Criticality.LOW);
        s.setActive(active);
        s.setLastSeenAt(lastSeen);
        s.setExpectedIntervalMinutes(interval);
        s.setToleranceMinutes(tolerance);
        return s;
    }

    @Test
    void inactiveWinsOverEverything() {
        assertThat(ServiceManager.deriveStatus(service(false, now, 5, 2), now)).isEqualTo("INACTIVE");
    }

    @Test
    void neverSeenIsUnknown() {
        assertThat(ServiceManager.deriveStatus(service(true, null, 5, 2), now)).isEqualTo("UNKNOWN");
    }

    @Test
    void recentLogIsHealthy() {
        assertThat(ServiceManager.deriveStatus(service(true, now.minusMinutes(3), 5, 2), now))
                .isEqualTo("HEALTHY");
    }

    @Test
    void logAtExactLimitIsHealthy() {
        assertThat(ServiceManager.deriveStatus(service(true, now.minusMinutes(7), 5, 2), now))
                .isEqualTo("HEALTHY");
    }

    @Test
    void overdueLogIsSilent() {
        assertThat(ServiceManager.deriveStatus(service(true, now.minusMinutes(8), 5, 2), now))
                .isEqualTo("SILENT");
    }

    @Test
    void noExpectedIntervalNeverGoesSilent() {
        assertThat(ServiceManager.deriveStatus(service(true, now.minusDays(30), null, null), now))
                .isEqualTo("HEALTHY");
    }

    @Test
    void nullToleranceTreatedAsZero() {
        assertThat(ServiceManager.deriveStatus(service(true, now.minusMinutes(6), 5, null), now))
                .isEqualTo("SILENT");
    }
}
